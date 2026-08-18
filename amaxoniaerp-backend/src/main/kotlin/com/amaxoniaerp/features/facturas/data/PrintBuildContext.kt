package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.features.facturas.domain.FacturaPrintPayloadResponse
import java.math.BigDecimal
import java.math.RoundingMode

/** Partes del payload de impresión ya resueltas (fila cabecera, productos, pagos, cambio). */
internal data class PrintBuildContext(
    val factura: SqlRow,
    val productos: List<com.amaxoniaerp.features.facturas.domain.ProductoPrintResponse>,
    val pagos: List<com.amaxoniaerp.features.facturas.domain.PagoPrintResponse>,
    val cambio: BigDecimal?,
    val subtotal: BigDecimal,
    val montoExento: BigDecimal,
    val totalImpuesto: BigDecimal,
    val total: BigDecimal,
    val descuentoTotal: BigDecimal,
    val isPanama: Boolean,
    val companyNameFallback: String,
)

internal fun printCountrySpecificFields(isPanama: Boolean): String =
    if (isPanama) {
        """
        cs.nombre_sucursal AS cliente_sucursal_nombre,
        cs.direccion AS cliente_sucursal_direccion,
        f.cufe,
        f.qr,
        f.fechaRecepcionDGI,
        f.puntoFacturacionFiscal,
        f.nroProtocoloAutorizacion,
        s.codigo_sucursal_emisor AS sucursal_codigo,
        NULL AS numero_control_thka,
        """.trimIndent()
    } else {
        """
        NULL AS cliente_sucursal_nombre,
        NULL AS cliente_sucursal_direccion,
        NULL AS cufe,
        NULL AS qr,
        NULL AS fechaRecepcionDGI,
        NULL AS puntoFacturacionFiscal,
        NULL AS nroProtocoloAutorizacion,
        NULL AS sucursal_codigo,
        f.numero_control_thka AS numero_control_thka,
        """.trimIndent()
    }

internal fun printClientBranchJoin(isPanama: Boolean): String =
    if (isPanama) {
        "LEFT JOIN cliente_sucursal cs ON cs.sucursal_id = f.cliente_sucursal_id"
    } else {
        ""
    }

internal fun printFacturaSql(
    facturaId: String,
    isPanama: Boolean,
): String =
    """
    SELECT
        f.id_factura,
        f.cod_factura,
        f.numeroDocumentoFiscal,
        f.fechaFactura,
        f.facturar_a,
        f.facturar_a_ruc,
        f.facturar_a_direccion,
        f.facturar_a_telefono,
        f.usuario_creacion,
        f.subtotal,
        f.totalizar_base_imponible,
        f.totalizar_monto_iva,
        f.TotalTotalFactura,
        f.totalizar_total_general,
        f.formapago,
        ${printCountrySpecificFields(isPanama)}
        pg.rif AS empresa_ruc,
        c.descripcion AS caja_descripcion,
        c.codigo AS caja_codigo,
        s.sucursal AS sucursal_nombre,
        s.descripcion AS sucursal_descripcion
    FROM factura f
    LEFT JOIN parametros_generales pg ON 1 = 1
    LEFT JOIN caja c ON c.id = f.id_caja
    LEFT JOIN sucursal s ON s.id = f.id_sucursal
    ${printClientBranchJoin(isPanama)}
    WHERE f.id_factura = '${facturaId.sqlLiteral()}'
    LIMIT 1
    """.trimIndent()

internal fun printProductos(facturaId: String): List<com.amaxoniaerp.features.facturas.domain.ProductoPrintResponse> =
    queryMany(
        """
        SELECT
            _item_descripcion,
            _item_codigo,
            _item_cantidad_total,
            _item_preciosiniva,
            _item_montodescuento,
            _item_piva,
            _item_totalsiniva,
            _item_totalconiva,
            _item_unidad_empaque
        FROM factura_detalle
        WHERE id_factura = '${facturaId.sqlLiteral()}'
        ORDER BY fecha_creacion ASC
        """.trimIndent(),
    ).map { row ->
        val totalSinIva = row.decimal("_item_totalsiniva")
        val taxRate = row.decimal("_item_piva")
        val impuesto = totalSinIva.multiply(taxRate).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
        com.amaxoniaerp.features.facturas.domain.ProductoPrintResponse(
            nombre = row.string("_item_descripcion"),
            cantidad = row.decimal("_item_cantidad_total").toMoneyString(trimZeros = true),
            unidad = row.stringOrNull("_item_unidad_empaque"),
            precioUnitario = row.decimal("_item_preciosiniva").toMoneyString(),
            descuento = row.decimal("_item_montodescuento").toMoneyString(),
            impuesto = impuesto.toMoneyString(),
            total = row.decimal("_item_totalconiva").toMoneyString(),
            codigo = row.stringOrNull("_item_codigo"),
            tasaImpuesto = taxRate.toMoneyString(trimZeros = true),
        )
    }

internal fun printPagos(
    facturaId: String,
    factura: SqlRow,
    total: BigDecimal,
): List<com.amaxoniaerp.features.facturas.domain.PagoPrintResponse> =
    queryMany(
        """
        SELECT
            cfp.descripcion AS metodo,
            cnd.monto
        FROM caja_nueva cn
        INNER JOIN caja_nueva_detalle cnd ON cnd.caja_id = cn.caja_id
        LEFT JOIN caja_forma_pago cfp ON cfp.id_forma_pago = cnd.id_forma_pago
        WHERE cn.id_factura = '${facturaId.sqlLiteral()}'
          AND cnd.monto > 0
        ORDER BY cnd.caja_detalle_id ASC
        """.trimIndent(),
    ).map { row ->
        com.amaxoniaerp.features.facturas.domain.PagoPrintResponse(
            metodo = row.stringOrNull("metodo").orEmpty().ifBlank { "PAGO" },
            monto = row.decimal("monto").toMoneyString(),
        )
    }.ifEmpty {
        listOf(
            com.amaxoniaerp.features.facturas.domain.PagoPrintResponse(
                metodo = factura.stringOrNull("formapago").orEmpty().ifBlank { "PAGO" },
                monto = total.toMoneyString(),
            ),
        )
    }

internal fun printCambio(facturaId: String): BigDecimal? =
    queryOne(
        """
        SELECT totalizar_cambio
        FROM factura_detalle_formapago
        WHERE id_factura = '${facturaId.sqlLiteral()}'
        LIMIT 1
        """.trimIndent(),
    )?.decimalOrNull("totalizar_cambio")

internal fun buildPrintResponse(ctx: PrintBuildContext): FacturaPrintPayloadResponse =
    FacturaPrintPayloadResponse(
        facturaId = ctx.factura.string("id_factura"),
        numeroFactura = ctx.factura.string("cod_factura"),
        fecha = ctx.factura.stringOrNull("fechaFactura").orEmpty(),
        empresa =
            com.amaxoniaerp.features.facturas.domain.EmpresaPrintResponse(
                nombre = ctx.companyNameFallback.ifBlank { "Amaxonia ERP" },
                ruc = ctx.factura.stringOrNull("empresa_ruc"),
                direccion = null,
                telefono = null,
                tienda =
                    ctx.factura.stringOrNull("sucursal_nombre") ?: ctx.factura.stringOrNull("sucursal_descripcion"),
                caja = ctx.factura.stringOrNull("caja_descripcion") ?: ctx.factura.stringOrNull("caja_codigo"),
            ),
        cliente =
            com.amaxoniaerp.features.facturas.domain.ClientePrintResponse(
                nombre =
                    ctx.factura
                        .stringOrNull("facturar_a")
                        .orEmpty()
                        .ifBlank { "Cliente General" },
                documento = ctx.factura.stringOrNull("facturar_a_ruc"),
                sucursal = ctx.factura.stringOrNull("cliente_sucursal_nombre"),
                sucursalDireccion = ctx.factura.stringOrNull("cliente_sucursal_direccion"),
            ),
        vendedor = ctx.factura.stringOrNull("usuario_creacion"),
        productos = ctx.productos,
        subtotal = ctx.subtotal.toMoneyString(),
        montoExento = ctx.montoExento.toMoneyString(),
        // Total line-item discount aggregated in BigDecimal. Always present in the
        // payload (never null) so the Android formatter can render the row unconditionally
        // and stay coherent with the on-screen Cobro.
        descuento = ctx.descuentoTotal.toMoneyString(),
        totalImpuesto = ctx.totalImpuesto.toMoneyString(),
        total = ctx.total.toMoneyString(),
        pagos = ctx.pagos,
        cambio = ctx.cambio?.toMoneyString(),
        qrUrl = ctx.factura.stringOrNull("qr"),
        cufe = ctx.factura.stringOrNull("cufe"),
        fechaRecepcionDgi = ctx.factura.stringOrNull("fechaRecepcionDGI"),
        proveedorAutorizado = if (ctx.isPanama) "The Factory HKA Corp." else null,
        numeroDocumentoFiscal = ctx.factura.stringOrNull("numeroDocumentoFiscal"),
        puntoFacturacionFiscal = ctx.factura.stringOrNull("puntoFacturacionFiscal"),
        codigoSucursal = ctx.factura.stringOrNull("sucursal_codigo"),
        protocoloAutorizacion = ctx.factura.stringOrNull("nroProtocoloAutorizacion"),
        // FASE 2.3b — Exponer número de control HKA persistido (Venezuela digital).
        // En Panamá el SELECT emite NULL; aquí nunca se inventa un valor.
        numeroControlThka = ctx.factura.stringOrNull("numero_control_thka"),
    )
