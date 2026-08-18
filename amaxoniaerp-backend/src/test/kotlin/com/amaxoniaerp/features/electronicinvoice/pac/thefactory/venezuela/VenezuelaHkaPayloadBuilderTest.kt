package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceVEContext
import com.amaxoniaerp.features.electronicinvoice.domain.VECajaData
import com.amaxoniaerp.features.electronicinvoice.domain.VECompradorData
import com.amaxoniaerp.features.electronicinvoice.domain.VEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.VECorrelativoReservado
import com.amaxoniaerp.features.electronicinvoice.domain.VEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.VEFacturaData
import com.amaxoniaerp.features.electronicinvoice.domain.VEFormaPagoData
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del [VenezuelaHkaPayloadBuilder].
 *
 * Cubren la matemática fiscal exclusiva de VE con BigDecimal:
 *  - IVA general (16%), reducido (8%) y exento (0%).
 *  - Descuento por línea.
 *  - IGTF: pago parcial en divisa y pago mixto (no se aplica 3% por defecto).
 *  - Multimoneda con tasa de cambio.
 *  - transaccionId determinista.
 *  - Vuelto / efectivo recibido.
 */
class VenezuelaHkaPayloadBuilderTest {
    private val builder = VenezuelaHkaPayloadBuilder()

    // ─── 15. IVA general 16% ────────────────────────────────────────────────

    @Test
    fun `IVA general 16 por ciento se agrupa y totaliza correctamente`() {
        val ctx =
            context {
                detalles =
                    listOf(
                        detalle {
                            precioSinIva = "100.00"
                            totalSinIva = "100.00"
                            totalConIva = "116.00"
                            piva =
                                "16.00"
                        },
                    )
                ; ivaTotalFactura = "16.00"
                totalizarTotalGeneral = "116.00"
            }

        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")

        with(payload.documento.totalesSubTotales) {
            assertEquals("100.00", totalPrecioNeto)
            assertEquals("16.00", totalIva)
            assertEquals("116.00", montoTotalFactura)
            assertEquals("100.00", totalAlicuotaGeneral)
            assertEquals("0.00", totalAlicuotaReducido)
            assertEquals("0.00", totalAlicuotaExento)
            assertEquals("100.00", totalMontoGravado)
            assertEquals("116.00", totalTodosItems)
            assertEquals(
                "16.00",
                payload.documento.listaItems
                    .single()
                    .alicuotaIva,
            )
        }
    }

    // ─── 16. IVA reducido 8% ────────────────────────────────────────────────

    @Test
    fun `IVA reducido 8 por ciento se agrupa separado del general`() {
        val ctx =
            context {
                detalles =
                    listOf(
                        detalle {
                            precioSinIva = "100.00"
                            totalSinIva = "100.00"
                            totalConIva = "108.00"
                            piva = "8.00"
                        },
                    )
                ; ivaTotalFactura = "8.00"
                totalizarTotalGeneral = "108.00"
            }

        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")

        with(payload.documento.totalesSubTotales) {
            assertEquals("0.00", totalAlicuotaGeneral)
            assertEquals("100.00", totalAlicuotaReducido)
            assertEquals("0.00", totalAlicuotaExento)
            assertEquals("100.00", totalMontoGravado)
            assertEquals("8.00", totalIva)
        }
    }

    // ─── 17. Producto exento ────────────────────────────────────────────────

    @Test
    fun `producto exento va a totalAlicuotaExento y no aporta a montoGravado`() {
        val ctx =
            context {
                detalles =
                    listOf(
                        detalle {
                            precioSinIva = "50.00"
                            totalSinIva = "50.00"
                            totalConIva = "50.00"
                            piva = "0.00"
                        },
                    )
                ; ivaTotalFactura = "0.00"
                totalizarTotalGeneral = "50.00"
            }

        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")

        with(payload.documento.totalesSubTotales) {
            assertEquals("0.00", totalAlicuotaGeneral)
            assertEquals("0.00", totalAlicuotaReducido)
            assertEquals("50.00", totalAlicuotaExento)
            assertEquals("0.00", totalMontoGravado)
            assertEquals("0.00", totalIva)
            assertEquals("50.00", montoTotalFactura)
        }
    }

    @Test
    fun `factura mixta con general reducido y exento separa las tres alicuotas`() {
        val ctx =
            context {
                detalles =
                    listOf(
                        detalle {
                            precioSinIva = "100.00"
                            totalSinIva = "100.00"
                            totalConIva = "116.00"
                            piva =
                                "16.00"
                        },
                        detalle {
                            descripcion = "Reducer"
                            precioSinIva = "100.00"
                            totalSinIva = "100.00"
                            totalConIva =
                                "108.00"
                            piva = "8.00"
                        },
                        detalle {
                            descripcion = "Exempt"
                            precioSinIva = "20.00"
                            totalSinIva = "20.00"
                            totalConIva =
                                "20.00"
                            piva = "0.00"
                        },
                    )
                ; ivaTotalFactura = "24.00"
                totalizarTotalGeneral = "244.00"
            }

        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")

        with(payload.documento.totalesSubTotales) {
            assertEquals("100.00", totalAlicuotaGeneral)
            assertEquals("100.00", totalAlicuotaReducido)
            assertEquals("20.00", totalAlicuotaExento)
            assertEquals("200.00", totalMontoGravado)
            assertEquals("220.00", totalPrecioNeto)
            assertEquals("24.00", totalIva)
            assertEquals("244.00", montoTotalFactura)
        }
    }

    // ─── 18. Descuento por línea ────────────────────────────────────────────

    @Test
    fun `descuento por linea se reparte y totaliza como totalDescuento`() {
        val ctx =
            context {
                detalles =
                    listOf(
                        detalle {
                            descripcion = "Promo"
                            cantidad = "2.000"
                            precioSinIva = "50.00"
                            montoDescuento =
                                "10.00"
                            totalSinIva = "90.00"
                            totalConIva = "104.40"
                            piva = "16.00"
                        },
                    )
                ; ivaTotalFactura = "14.40"
                descuentosItemFactura = "10.00"
                totalizarTotalGeneral = "104.40"
            }

        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")

        val item = payload.documento.listaItems.single()
        assertEquals("10.00", item.montoDescuento)
        // precioUnitarioDescuento = montoDescuento / cantidad = 10 / 2 = 5
        assertEquals("5.00", item.precioUnitarioDescuento)
        assertEquals("90.00", item.precioItem)
        assertEquals("104.40", item.valorTotal)
        assertEquals("10.00", payload.documento.totalesSubTotales.totalDescuento)
    }

    // ─── 19. Pago completo en VES ───────────────────────────────────────────

    @Test
    fun `pago completo en VES no genera nodo IGTF`() {
        val ctx =
            context {
                totalizarTotalGeneral = "116.00"
                ivaTotalFactura = "16.00"
                formasPago =
                    listOf(
                        VEFormaPagoData(
                            idFormaPago = 1,
                            descripcion = "EFECTIVO",
                            siglas = "EF",
                            formaPagoFact = "01",
                            monto = BigDecimal("116.00"),
                            esDivisa = false,
                            montoRecibido = BigDecimal("120.00"),
                            tipoMoneda = "V",
                        ),
                    )
            }

        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")

        assertNull(payload.documento.totalesSubTotales.igtf)
        with(payload.documento.totalesSubTotales) {
            // totalValorRecibido = suma de montos de las formas = 116 (la denominación recibida
            // se modela aparte via montoRecibido; el PAC recibe el valor de la operación).
            assertEquals("116.00", totalValorRecibido)
            assertNull(vuelto) // recibido == total → no hay vuelto global
            assertEquals("01", listaFormaPago.single().formaPagoFact)
            // El cambio se reporta por forma de pago usando (montoRecibido - monto).
            // El builder formatea sin zero-pad a la izquierda (BigDecimal HALF_UP, escala 2).
            assertEquals("4.00", listaFormaPago.single().cambio)
            assertEquals("120.00", listaFormaPago.single().montoRecibido)
            assertNull(listaFormaPago.single().montoMonedaSecundaria)
        }
    }

    // ─── 20. Pago parcial en divisa con IGTF ────────────────────────────────

    @Test
    fun `pago parcial en divisa genera IGTF sobre la base convertida a VES`() {
        // Total factura = 1000 VES, tasa=100, IGTF=3%.
        // Cliente paga 5 USD (500 VES) en divisa + 500 VES en efectivo.
        // Base IGTF = 5 * 100 = 500 VES. IGTF = 3% * 500 = 15 VES.
        val ctx =
            context {
                multiMoneda = "SI"
                tasa = BigDecimal("100")
                totalizarTotalGeneral = "1000.00"
                ivaTotalFactura =
                    "0.00"
                igtf = BigDecimal("3.000000")
                formasPago =
                    listOf(
                        VEFormaPagoData(
                            idFormaPago = 2,
                            descripcion = "DIVISA",
                            siglas = "US",
                            formaPagoFact = "02",
                            monto = BigDecimal("5.00"),
                            esDivisa = true,
                            montoRecibido = null,
                            tipoMoneda = "D",
                        ),
                        VEFormaPagoData(
                            idFormaPago = 1,
                            descripcion = "EFECTIVO",
                            siglas = "EF",
                            formaPagoFact = "01",
                            monto = BigDecimal("500.00"),
                            esDivisa = false,
                            montoRecibido = null,
                            tipoMoneda = "V",
                        ),
                    )
            }

        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")

        val igtf = payload.documento.totalesSubTotales.igtf
        assertTrue(igtf != null, "Debe generarse nodo IGTF cuando hay divisa")
        assertEquals("500.00", igtf.baseImponible)
        assertEquals("3.00", igtf.porcentaje)
        assertEquals("15.00", igtf.montoIgtf)
        // La forma en divisa reporta su monto convertido a VES.
        val fpDivisa =
            payload.documento.totalesSubTotales.listaFormaPago
                .first { it.esDivisaMoneda() || it.formaPagoFact == "02" }
        assertEquals("500.00", fpDivisa.montoMonedaSecundaria)
    }

    // ─── 21. Pago mixto ─────────────────────────────────────────────────────

    @Test
    fun `pago mixto VES mas divisa reporta ambas formas y un solo IGTF`() {
        val ctx =
            context {
                multiMoneda = "SI"
                tasa = BigDecimal("50")
                totalizarTotalGeneral = "600.00"
                ivaTotalFactura =
                    "0.00"
                igtf = BigDecimal("2.000000")
                formasPago =
                    listOf(
                        VEFormaPagoData(1, "EFECTIVO", "EF", "01", BigDecimal("400.00"), false, null, "V"),
                        VEFormaPagoData(2, "ZELLE", "ZL", "02", BigDecimal("4.00"), true, null, "D"),
                    )
            }

        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        val fps = payload.documento.totalesSubTotales.listaFormaPago
        assertEquals(2, fps.size)
        // Base IGTF = 4 USD * 50 = 200 VES → 2% = 4 VES.
        assertEquals(
            "4.00",
            payload.documento.totalesSubTotales.igtf
                ?.montoIgtf,
        )
    }

    // ─── 22. Multimoneda ────────────────────────────────────────────────────

    @Test
    fun `multimoneda SI con tasa mayor a 1 emite montoTotalMonedaSecundaria y tasaCambio`() {
        val ctx =
            context {
                multiMoneda = "SI"
                tasa = BigDecimal("100")
                totalizarTotalGeneral = "1160.00"
                ivaTotalFactura =
                    "160.00"
            }

        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")

        with(payload.documento.totalesSubTotales) {
            assertEquals("11.60", montoTotalMonedaSecundaria)
            assertEquals("100.00", tasaCambio)
        }
    }

    @Test
    fun `multimoneda NO omite moneda secundaria y tasa`() {
        val ctx =
            context {
                multiMoneda = "NO"
                tasa = BigDecimal("100")
            }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertNull(payload.documento.totalesSubTotales.montoTotalMonedaSecundaria)
        assertNull(payload.documento.totalesSubTotales.tasaCambio)
    }

    @Test
    fun `multimoneda SI pero tasa 1 omite conversion de moneda secundaria`() {
        val ctx =
            context {
                multiMoneda = "SI"
                tasa = BigDecimal("1")
            }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertNull(payload.documento.totalesSubTotales.montoTotalMonedaSecundaria)
    }

    // ─── transaccionId determinista ──────────────────────────────────────────

    @Test
    fun `transaccionId es determinista para mismo idFactura y numero`() {
        val ctx = context { idFactura = "abc-123" }
        val p1 = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000099")
        val p2 = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000099")
        val p3 = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000100")

        assertEquals(p1.documento.datosTransaccion.transaccionId, p2.documento.datosTransaccion.transaccionId)
        assertNotEquals(p1.documento.datosTransaccion.transaccionId, p3.documento.datosTransaccion.transaccionId)
        // 32 hex chars (top 16 bytes).
        assertEquals(32, p1.documento.datosTransaccion.transaccionId.length)
    }

    // ─── numeroPacFormateado ─────────────────────────────────────────────────

    @Test
    fun `numeroPacFormateado respeta longitud del formato con ceros iniciales`() {
        val reservado = VECorrelativoReservado(numero = 42, formato = 8)
        assertEquals("00000042", builder.numeroPacFormateado(reservado, remoto = null))
    }

    @Test
    fun `numeroPacFormateado toma el maximo entre local y remoto_mas_uno`() {
        val reservado = VECorrelativoReservado(numero = 10, formato = 8)
        // Local=10, remoto=15 → remoto+1=16 gana.
        assertEquals("00000016", builder.numeroPacFormateado(reservado, remoto = 15))
        // Local=20, remoto=15 → local gana.
        val reservadoMayor = VECorrelativoReservado(numero = 20, formato = 8)
        assertEquals("00000020", builder.numeroPacFormateado(reservadoMayor, remoto = 15))
    }

    // ─── cliente ─────────────────────────────────────────────────────────────

    @Test
    fun `cliente sin datos se completa con consumidor final`() {
        val ctx =
            context {
                comprador =
                    VECompradorData(
                        nombreRazonSocial = "",
                        rif = "",
                        direccion = null,
                        telefono = null,
                        email = null,
                    )
            }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals("CONSUMIDOR FINAL", payload.documento.datosTransaccion.cliente.nombreRazonSocial)
        assertEquals("V000000000", payload.documento.datosTransaccion.cliente.numeroRif)
    }

    // ─── items vacíos no rompen el builder ───────────────────────────────────

    @Test
    fun `factura sin formas de pago emite una forma por defecto`() {
        val ctx = context { formasPago = emptyList() }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals(1, payload.documento.totalesSubTotales.listaFormaPago.size)
        assertEquals(
            "01",
            payload.documento.totalesSubTotales.listaFormaPago
                .first()
                .formaPagoFact,
        )
        assertEquals(
            "0.00",
            payload.documento.totalesSubTotales.listaFormaPago
                .first()
                .montoPagado,
        )
    }

    @Test
    fun `trip de valorItem respeta escala 2 y rounding HALF_UP`() {
        val ctx =
            context {
                detalles =
                    listOf(
                        detalle {
                            precioSinIva = "100.005"
                            totalSinIva = "100.005"
                            totalConIva = "116.006"
                            piva =
                                "16.00"
                        },
                    )
            }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        val item = payload.documento.listaItems.single()
        assertEquals("100.01", item.precioUnitario) // HALF_UP
        assertEquals("100.01", item.precioItem)
        assertEquals("116.01", item.valorTotal)
    }

    // ─── FASE 1.1 — Ítem 6: montoEnLetras venezolano real ─────────────────

    @Test
    fun `montoEnLetras - factura de 116 VES produce la cadena esperada VE`() {
        val ctx = context { totalizarTotalGeneral = "116.00" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals(
            "CIENTO DIECISÉIS BOLÍVARES CON 00/100",
            payload.documento.totalesSubTotales.montoEnLetras,
        )
    }

    @Test
    fun `montoEnLetras - cero exacto`() {
        val ctx = context { totalizarTotalGeneral = "0.00" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals("CERO BOLÍVARES CON 00/100", payload.documento.totalesSubTotales.montoEnLetras)
    }

    @Test
    fun `montoEnLetras - un bolivar exacto usa singular`() {
        val ctx = context { totalizarTotalGeneral = "1.00" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals("UN BOLÍVAR CON 00/100", payload.documento.totalesSubTotales.montoEnLetras)
    }

    @Test
    fun `montoEnLetras - cien exacto no se confunde con ciento`() {
        val ctx = context { totalizarTotalGeneral = "100.00" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals("CIEN BOLÍVARES CON 00/100", payload.documento.totalesSubTotales.montoEnLetras)
    }

    @Test
    fun `montoEnLetras - mil exacto`() {
        val ctx = context { totalizarTotalGeneral = "1000.00" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals("MIL BOLÍVARES CON 00/100", payload.documento.totalesSubTotales.montoEnLetras)
    }

    @Test
    fun `montoEnLetras - un millon exacto usa singular`() {
        val ctx = context { totalizarTotalGeneral = "1000000.00" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals("UN MILLÓN BOLÍVARES CON 00/100", payload.documento.totalesSubTotales.montoEnLetras)
    }

    @Test
    fun `montoEnLetras - millones plural con miles`() {
        val ctx = context { totalizarTotalGeneral = "1500000.00" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals(
            "UN MILLÓN QUINIENTOS MIL BOLÍVARES CON 00/100",
            payload.documento.totalesSubTotales.montoEnLetras,
        )
    }

    @Test
    fun `montoEnLetras - centavos distintos de cero`() {
        val ctx = context { totalizarTotalGeneral = "16.50" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals("DIECISÉIS BOLÍVARES CON 50/100", payload.documento.totalesSubTotales.montoEnLetras)
    }

    @Test
    fun `montoEnLetras - veintiuno a veintinueve usan forma compacta`() {
        val ctx = context { totalizarTotalGeneral = "21.00" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals("VEINTIUNO BOLÍVARES CON 00/100", payload.documento.totalesSubTotales.montoEnLetras)
    }

    @Test
    fun `montoEnLetras - decenas con unidades usan Y`() {
        val ctx = context { totalizarTotalGeneral = "35.00" }
        val payload = builder.build(ctx, serie = "L001P001", numeroDocumentoFiscalFinal = "00000001")
        assertEquals("TREINTA Y CINCO BOLÍVARES CON 00/100", payload.documento.totalesSubTotales.montoEnLetras)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun VenezuelaHkaFormaPago.esDivisaMoneda(): Boolean {
        val paymentDescription = descripcion
        return paymentDescription?.contains("DIVISA", ignoreCase = true) == true
    }

    private class ContextSpec {
        var idFactura: String = "factura-1"
        var detalles: List<VEDetalleData>? = null
        var ivaTotalFactura: String = "16.00"
        var descuentosItemFactura: String = "0.00"
        var totalizarTotalGeneral: String = "116.00"
        var multiMoneda: String = "NO"
        var tasa: BigDecimal = BigDecimal("1")
        var igtf: BigDecimal = BigDecimal("3.000000")
        var formasPago: List<VEFormaPagoData>? = null
        var comprador: VECompradorData = VECompradorData("CLIENTE PRUEBA", "J12345678", "Caracas", "0212-1234567", null)
    }

    private fun context(configure: ContextSpec.() -> Unit = {}): InvoiceVEContext {
        val spec = ContextSpec().apply(configure)
        val formasPagoResueltas =
            spec.formasPago
                ?: listOf(
                    VEFormaPagoData(
                        1,
                        "EFECTIVO",
                        "EF",
                        "01",
                        BigDecimal(spec.totalizarTotalGeneral),
                        false,
                        null,
                        "V",
                    ),
                )
        val detallesResueltos =
            spec.detalles
                ?: listOf(
                    detalle {
                        precioSinIva = "100.00"
                        totalSinIva = "100.00"
                        totalConIva = "116.00"
                        piva = "16.00"
                    },
                )
        return InvoiceVEContext(
            config = buildVEConfig(spec),
            factura = buildVEFactura(spec),
            comprador = spec.comprador,
            detalles = detallesResueltos,
            formasPago = formasPagoResueltas,
            caja =
                VECajaData(
                    idCaja = "caja-1",
                    serieCaja = "L001",
                    serieSucursal = "L001P001",
                    codigoSucursalEmisor = "0000",
                    puntoFacturacionFiscal = "001",
                ),
            correlativoReservado = VECorrelativoReservado(1, 8),
        )
    }

    private fun buildVEConfig(spec: ContextSpec) =
        VEConfigData(
            tipoFacturacion = 5,
            tipoEntornoVe = 0,
            tokenEmpresa = "USER",
            tokenPassword = "PWD",
            baseUrl = "https://demo.thefactoryhka.com",
            rif = "J123456789",
            nombreEmpresa = "EMPRESA TEST",
            direccion = "DIR TEST",
            telefonos = "0212",
            igtf = spec.igtf,
            procesoGeneracion = "1",
            tipoEmision = "01",
            codigoSucursalEmisorFallback = "0000",
            puntoFacturacionFiscalFallback = "001",
        )

    private fun buildVEFactura(spec: ContextSpec) =
        VEFacturaData(
            idFactura = spec.idFactura,
            codFactura = "0000001",
            numeroDocumentoFiscal = null,
            numeroControlThka = null,
            tipoDocumento = "01",
            fechaFactura = "2026-08-03",
            fechaCreacion = "2026-08-03 10:00:00",
            facturarANombre = "CLIENTE PRUEBA",
            facturarARuc = "J12345678",
            facturarADireccion = "Caracas",
            facturarATelefono = "0212-1234567",
            totalTotalFactura = BigDecimal(spec.totalizarTotalGeneral),
            ivaTotalFactura = BigDecimal(spec.ivaTotalFactura),
            descuentosItemFactura = BigDecimal(spec.descuentosItemFactura),
            totalizarBaseImponible = BigDecimal("100.00"),
            totalizarMontoIva = BigDecimal(spec.ivaTotalFactura),
            totalizarTotalGeneral = BigDecimal(spec.totalizarTotalGeneral),
            montoItemsFactura = BigDecimal("100.00"),
            multiMoneda = spec.multiMoneda,
            tasa = spec.tasa,
            monedaBase = 1,
            abrMonedaBase = "VES",
            monedaSecundaria = 2,
            abrMonedaSecundaria = "USD",
        )

    private class DetalleSpec {
        var descripcion: String = "Producto de prueba"
        var codigo: String = "P001"
        var referencia: String? = "REF-1"
        var unidadEmpaque: String? = "UND"
        var cantidad: String = "1.000"
        var precioSinIva: String = "100.00"
        var descuento: String = "0.00"
        var montoDescuento: String = "0.00"
        var piva: String = "16.00"
        var totalSinIva: String = "100.00"
        var totalConIva: String = "116.00"
        var importeIsc: String? = null
        var porcentajeIsc: String? = null
        var importeOti: String? = null
        var importeAcarreo: String? = null
        var importeSeguro: String? = null
    }

    private fun detalle(configure: DetalleSpec.() -> Unit): VEDetalleData {
        val spec = DetalleSpec().apply(configure)
        return VEDetalleData(
            descripcion = spec.descripcion,
            codigo = spec.codigo,
            referencia = spec.referencia,
            unidadEmpaque = spec.unidadEmpaque,
            cantidad = BigDecimal(spec.cantidad),
            precioSinIva = BigDecimal(spec.precioSinIva),
            descuento = BigDecimal(spec.descuento),
            montoDescuento = BigDecimal(spec.montoDescuento),
            piva = BigDecimal(spec.piva),
            totalSinIva = BigDecimal(spec.totalSinIva),
            totalConIva = BigDecimal(spec.totalConIva),
            importeIsc = spec.importeIsc?.let(::BigDecimal),
            porcentajeIsc = spec.porcentajeIsc?.let(::BigDecimal),
            importeOti = spec.importeOti?.let(::BigDecimal),
            importeAcarreo = spec.importeAcarreo?.let(::BigDecimal),
            importeSeguro = spec.importeSeguro?.let(::BigDecimal),
        )
    }
}
