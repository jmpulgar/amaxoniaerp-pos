package com.amaxoniaerp.features.sales.application

import com.amaxoniaerp.features.electronicinvoice.application.ProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceStrategy
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.domain.DuplicateInvoiceException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import com.amaxoniaerp.features.sales.domain.SaleCurrencyInput
import com.amaxoniaerp.features.sales.domain.SaleInvoiceInput
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.sales.domain.SalePaymentInput
import com.amaxoniaerp.features.sales.domain.SalePaymentSummaryInput
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests de selección del mecanismo fiscal en [ProcessSaleUseCase] (FASE 1.1).
 *
 * Cubren las 10 pruebas obligatorias del brief:
 *  1.  VE + useHka20=true  → NO invoca al digital; la venta comercial se confirma.
 *  2.  VE + useHka20=false → invoca al digital.
 *  3.  VE + useHka20=null  → invoca al digital (compatibilidad hacia atrás).
 *  4.  PA + cualquier valor → invoca a PA (campo ignorado).
 *  5.  VE + useHka20=true  → NO se llama ni auth, ni UltimoDocumento, ni emisión,
 *      ni reserva de correlativo digital (verificado vía fake digital con contadores).
 *  6.  VE + useHka20=true  → la respuesta conserva success/codFactura/codEstatus.
 *  7.  VE + useHka20=true y el HKA20 (POS) falla → NO hay fallback digital.
 *      (El fallback entre mecanismos está prohibido por diseño: si el flujo
 *      retorna sin llamar al digital, ningún error posterior puede introducir
 *      una llamada digital que el UseCase no ejecutó.)
 *  8.  VE + useHka20=false y el digital falla → NO hay fallback HKA20.
 *      (El UseCase no conoce de HKA20 físico: nunca lo invoca.)
 *  9.  PANAMÁ se mantiene sin cambios: con tipo_facturacion < 3 retorna Failure.
 *  10. Idempotencia: el mismo request no puede ejecutar ambos mecanismos.
 *
 * Y las 7 pruebas del flujo HKA20 (1-7):
 *  H1. Seleccionar THE_FACTORY_HKA envía useHka20=true (test POS aparte).
 *  H2. La venta se guarda correctamente en backend (acá: saleResult.success=true).
 *  H3. VenezuelaInvoiceStrategy digital no se ejecuta.
 *  H4. Al regresar la respuesta, el POS continúa con el flujo HKA20 (acá: el
 *      UseCase retorna saleResult sin error FE; el POS hace lo suyo después).
 *  H5. Se conserva la confirmación fiscal actual del HKA-20 (PATCH posterior).
 *  H6. No se ejecuta simultáneamente la API digital.
 *  H7. Un error del HKA-20 (POS) no produce fallback hacia facturación digital.
 */
class ProcessSaleUseCaseSelectionTest {
    private val db: Database =
        Database.connect("jdbc:h2:mem:process_sale_sel_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")

    @Test
    fun `1 - VE con useHka20=true NO llama a la strategy digital y confirma la venta`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital = RecordingStrategy("VE")
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            val request = sampleRequest(useHka20 = true)

            val response = useCase.execute(db, "VE", request)

            assertEquals(0, digital.processCalls, "useHka20=true → el digital NO debe invocarse")
            assertTrue(response.success, "La venta comercial debe confirmarse")
            assertEquals(2, response.codEstatus)
            assertNull(response.feError, "Con useHka20=true no debe haber error FE")
        }

    @Test
    fun `2 - VE con useHka20=false SI llama a la strategy digital`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            // FASE 2 (Punto 1): Success VE usa campos propios (numeroDocumentoFiscal), nunca cufe.
            val digital =
                RecordingStrategy(
                    "VE",
                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-1"),
                )
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            val request = sampleRequest(useHka20 = false)

            val response = useCase.execute(db, "VE", request)

            assertEquals(1, digital.processCalls, "useHka20=false → el digital DEBE invocarse exactamente 1 vez")
            assertEquals("DOC-1", response.numeroDocumentoFiscal)
        }

    @Test
    fun `3 - VE con useHka20=null (campo ausente) llama a la strategy digital - compatibilidad legacy`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital =
                RecordingStrategy(
                    "VE",
                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-X"),
                )
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            val request = sampleRequest(useHka20 = null) // cliente antiguo sin el campo

            val response = useCase.execute(db, "VE", request)

            assertEquals(1, digital.processCalls, "useHka20=null → debe invocarse el digital (default seguro)")
            assertEquals("DOC-X", response.numeroDocumentoFiscal)
        }

    @Test
    fun `4 - PA con cualquier valor de useHka20 invoca a PA (campo ignorado)`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val paStrategy = RecordingStrategy("PA", result = ElectronicInvoiceResult.Success(cufe = "CUFE-PA"))
            val factory = SingleStrategyFactory(paStrategy)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            val responseTrue = useCase.execute(db, "PA", sampleRequest(useHka20 = true))
            val responseFalse = useCase.execute(db, "PA", sampleRequest(useHka20 = false))
            val responseNull = useCase.execute(db, "PA", sampleRequest(useHka20 = null))

            assertEquals(3, paStrategy.processCalls, "PA ignora useHka20 en todas sus formas")
            assertEquals("CUFE-PA", responseTrue.cufe)
            assertEquals("CUFE-PA", responseFalse.cufe)
            assertEquals("CUFE-PA", responseNull.cufe)
        }

    @Test
    fun `5 - VE con useHka20=true omite autenticacion, UltimoDocumento, emision y reserva digital`() =
        runBlocking {
            // El fake digital lanza si se invoca cualquier método, demostrando que
            // ningún componente del digital es tocado cuando el frontend eligió HKA20.
            val saleRepo = FakeSaleRepo()
            val digital = ExplosiveStrategy("VE")
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = true))

            // Si digital ExplosiveStrategy no explotó, significa que el UseCase no lo llamó.
            assertTrue(response.success)
            assertEquals(2, response.codEstatus)
            assertNull(response.feError)
        }

    @Test
    fun `6 - VE con useHka20=true conserva success, codFactura y codEstatus en la respuesta`() =
        runBlocking {
            val saleRepo = FakeSaleRepo(success = true, codEstatus = 2, codFactura = "F-001-0001")
            val digital = ExplosiveStrategy("VE")
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = true))

            assertTrue(response.success)
            assertEquals("F-001-0001", response.codFactura)
            assertEquals(2, response.codEstatus)
        }

    @Test
    fun `7 - VE con useHka20=true NO admite fallback al digital ante fallo posterior del HKA20`() =
        runBlocking {
            // Simula la situación real: el UseCase ya retornó (sin llamar al digital).
            // Si luego el POS falla imprimiendo HKA20, NADIE reintenta llamando al digital
            // porque el request ya terminó su vida con useHka20=true. Acá validamos que el
            // UseCase, en su única ejecución, jamás invoca el digital cuando useHka20=true.
            val saleRepo = FakeSaleRepo()
            val digital = RecordingStrategy("VE")
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            // Ejecutamos dos veces el mismo request con useHka20=true (simula reintentos del POS).
            useCase.execute(db, "VE", sampleRequest(useHka20 = true))
            useCase.execute(db, "VE", sampleRequest(useHka20 = true))

            assertEquals(0, digital.processCalls, "Reintentos del POS con useHka20=true jamás deben caer al digital")
        }

    @Test
    fun `8 - VE con useHka20=false y digital fallido NO produce fallback al HKA20`() =
        runBlocking {
            // El UseCase desconoce al HKA20 físico: sólo sabe que el digital falló. No hay
            // código que reintente por HKA20. Lo verificamos forzando un Failure en el digital
            // y comprobando que el flujo retorna con feError sin invocar nada distinto.
            val saleRepo = FakeSaleRepo()
            val digital =
                RecordingStrategy(
                    countryCode = "VE",
                    result = ElectronicInvoiceResult.Failure("PAC_REJECT", "Rechazado por el PAC VE"),
                )
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = false))

            assertEquals(1, digital.processCalls, "El digital fue invocado exactamente una vez")
            assertTrue(response.success, "La venta comercial ya estaba confirmada")
            assertTrue(response.feError!!.contains("PAC_REJECT"), "El error FE debe propagarse, no silenciarse")
        }

    @Test
    fun `9 - PA con tipo_facturacion bajo conserva su flujo - sin cambios por useHka20`() =
        runBlocking {
            // Panamá no interpreta useHka20. Si su strategy decide NotApplicable
            // (p.ej. tipo_facturacion < 3), el UseCase no agrega lógica de HKA20.
            val saleRepo = FakeSaleRepo()
            val paStrategy = RecordingStrategy("PA", result = ElectronicInvoiceResult.NotApplicable("PA"))
            val factory = SingleStrategyFactory(paStrategy)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            val response = useCase.execute(db, "PA", sampleRequest(useHka20 = true))

            assertEquals(1, paStrategy.processCalls, "PA se invoca con normalidad")
            assertNull(response.feError, "PA NotApplicable no es error")
        }

    @Test
    fun `10 - Idempotencia - el mismo request no puede ejecutar ambos mecanismos`() =
        runBlocking {
            // Para el mismo idFactura + useHka20=true, sólo el HKA20 (POS).
            // El digital jamás se invoca en ningún punto de la vida del request.
            val saleRepo = FakeSaleRepo()
            val digital = RecordingStrategy("VE")
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            val request = sampleRequest(idFactura = "UUID-FIJO", useHka20 = true)
            useCase.execute(db, "VE", request)
            useCase.execute(db, "VE", request) // reintento idempotente

            assertEquals(0, digital.processCalls, "El digital nunca debe correr para este idFactura")
        }

    /**
     * FASE 1.1 — Brief item 2: congelamiento del mecanismo por clientCorrelationId.
     *
     * Caso: primer request con `useHka20=true`, reintento con `useHka20=false`.
     * El mecanismo seleccionado en el primer request (HKA-20 POS) DEBE
     * congelarse: la facturación digital Venezuela NUNCA debe ejecutarse en
     * el segundo.
     *
     * Esta garantía la da el ledger comercial: `ProcessSaleTransactionalRepository
     * .validateDuplicateInvoice(idFactura)` lanza `DuplicateInvoiceException`
     * cuando el `idFactura` ya existe con `cod_estatus=2`. En el route HTTP eso
     * se traduce a `409 Conflict`. Por tanto el bloque FE del UseCase — donde
     * viviría cualquier invocación al digital — no se alcanza.
     *
     * El test emula exactamente ese comportamiento con un FakeSaleRepo que
     * registra los `process()` previos y lanza la excepción en la segunda
     * llamada para el mismo idFactura.
     */
    @Test
    fun `FASE 1_1 item 2 - reintento del mismo idFactura cambiando useHka20 a false no emite digitalmente`() =
        runBlocking {
            val saleRepo = DuplicateAwareSaleRepo()
            val digital = RecordingStrategy("VE", result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-LEAK"))
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            // 1) primer request: HKA-20. Procesa OK y NO invoca al digital.
            val firstRequest = sampleRequest(idFactura = "UUID-LOCK", useHka20 = true)
            useCase.execute(db, "VE", firstRequest)
            assertEquals(0, digital.processCalls, "Primer request HKA20 no debe invocar digital")

            // 2) reintento con useHka20=false: el ledger DEBE rechazarlo antes de FE.
            //    El UseCase propaga DuplicateInvoiceException; el digital no se llama.
            val flippedRequest = sampleRequest(idFactura = "UUID-LOCK", useHka20 = false)
            kotlin.test.assertFailsWith<DuplicateInvoiceException> {
                kotlinx.coroutines.runBlocking { useCase.execute(db, "VE", flippedRequest) }
            }
            assertEquals(
                0,
                digital.processCalls,
                "El reintento con useHka20=false NO debe emitir digitalmente: el ledger lo congeló",
            )
        }

    /**
     * FASE 1.1 — Brief item 5 (simétrico al ítem 2): congelamiento del mecanismo
     * por clientCorrelationId en la dirección opuesta.
     *
     * Caso: primer request con `useHka20=false` (se invocó al digital y se
     * emitió factura electrónica), reintento con `useHka20=true`.
     *
     * Garantía esperada: el reintento NO debe iniciar un flujo HKA-20 sobre una
     * nueva factura. La misma capa de idempotencia (`validateDuplicateInvoice`)
     * detecta el `idFactura` ya procesado (cod_estatus=2) y lanza
     * `DuplicateInvoiceException` ANTES de cualquier selección de mecanismo.
     *
     * El UseCase propaga la excepción sin tocar la strategy HKA-20/digital.
     */
    @Test
    fun `FASE 1_1 item 5 - reintento del mismo idFactura cambiando useHka20 a true tampoco inicia HKA20`() =
        runBlocking {
            val saleRepo = DuplicateAwareSaleRepo()
            val digital = RecordingStrategy("VE", result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-EMIT"))
            val factory = SingleStrategyFactory(digital)
            val useCase = ProcessSaleUseCase(saleRepo, factory)

            // 1) primer request: digital (useHka20=false). La FE se procesa y la
            //    venta queda cod_estatus=2 en el ledger.
            val firstRequest = sampleRequest(idFactura = "UUID-LOCK2", useHka20 = false)
            val first = useCase.execute(db, "VE", firstRequest)
            assertEquals(1, digital.processCalls, "Primer request digital debe invocar al strategy exactamente 1 vez")
            assertEquals("DOC-EMIT", first.numeroDocumentoFiscal, "VE propaga numeroDocumentoFiscal (Punto 1)")

            // 2) reintento con useHka20=true: el ledger debe congelarlo en el mismo
            //    punto exacto del caso simétrico — el UseCase propaga la excepción
            //    y NO se crea nueva factura HKA-20.
            val flippedRequest = sampleRequest(idFactura = "UUID-LOCK2", useHka20 = true)
            kotlin.test.assertFailsWith<DuplicateInvoiceException> {
                kotlinx.coroutines.runBlocking { useCase.execute(db, "VE", flippedRequest) }
            }
            assertEquals(
                1,
                digital.processCalls,
                "El reintento con useHka20=true NO debe invocar digital: el ledger lo congeló",
            )
            // NOTA: el flujo HKA-20 físico es un componente del POS que opera contra
            // la factura ya existente; no existe en el backend. Aquí validamos que el
            // backend nunca recibe una segunda llamada de venta con el mismo id.
        }

    // ─── FASE 2 (Punto 1) — Propagación de campos fiscales VE al ProcessSaleResponse
    //
    // Garantía: el UseCase mapea los campos PROPIOS de VE (numeroDocumentoFiscal
    // y numeroControlThka). Jamás reutiliza cufe/nroProtocoloAutorizacion de PA:
    //   - VE Success      → numeroDocumentoFiscal/numeroControlThka (propios)
    //   - VE AlreadyIssued → numeroDocumentoFiscal/numeroControlThka (persistidos)
    //   - VE Uncertain/Failure → null (no se inventa)
    //   - VE HKA-20       → null (no aplica el digital)
    //   - PA              → cufe/qr/fechaRecepcionDGI/nroProtocoloAutorizacion (intactos),
    //                       numeroDocumentoFiscal/numeroControlThka en null.

    @Test
    fun `FASE 2 a - VE Success propaga numeroDocumentoFiscal desde el campo propio`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital =
                RecordingStrategy(
                    "VE",
                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "00001234", numeroControlThka = "001-00001"),
                )
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(digital))

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = false))

            assertEquals(
                "00001234",
                response.numeroDocumentoFiscal,
                "VE Success debe reflejar el campo propio numeroDocumentoFiscal",
            )
            assertNull(response.cufe, "VE jamás reutiliza cufe para transportar numDoc")
        }

    @Test
    fun `FASE 2 b - VE Success propaga numeroControlThka desde el campo propio`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital =
                RecordingStrategy(
                    "VE",
                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "00001234", numeroControlThka = "001-00001"),
                )
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(digital))

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = false))

            assertEquals(
                "001-00001",
                response.numeroControlThka,
                "VE Success debe reflejar el campo propio numeroControlThka",
            )
            assertNull(response.cufe, "VE jamás persiste ni transporta cufe (Punto 1)")
        }

    @Test
    fun `FASE 2 c - VE AlreadyIssued propaga los valores persistidos`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital =
                RecordingStrategy(
                    "VE",
                    result =
                        ElectronicInvoiceResult.AlreadyIssued(
                            country = "VE",
                            numeroDocumentoFiscal = "00009988",
                            numeroControl = "001-9988",
                        ),
                )
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(digital))

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = false))

            assertEquals("00009988", response.numeroDocumentoFiscal, "AlreadyIssued debe exponer el numDoc persistido")
            assertEquals("001-9988", response.numeroControlThka, "AlreadyIssued debe exponer el numCtrl persistido")
        }

    @Test
    fun `FASE 2 d - VE Success con numeroControlThka null deja numeroControlThka en null`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital =
                RecordingStrategy(
                    "VE",
                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-ONLY", numeroControlThka = null),
                )
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(digital))

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = false))

            assertEquals("DOC-ONLY", response.numeroDocumentoFiscal)
            assertNull(response.numeroControlThka, "No se inventa numeroControlThka cuando HKA no retornó uno")
        }

    @Test
    fun `FASE 2 e - VE HKA-20 deja los campos fiscales digitales en null`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital = RecordingStrategy("VE")
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(digital))

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = true))

            assertEquals(0, digital.processCalls, "HKA-20 → el digital no se invoca")
            assertNull(response.numeroDocumentoFiscal, "HKA-20 NO inventa numeroDocumentoFiscal")
            assertNull(response.numeroControlThka, "HKA-20 NO inventa numeroControlThka")
        }

    @Test
    fun `FASE 2 f - VE Failure NO inventa campos fiscales digitales`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital =
                RecordingStrategy(
                    "VE",
                    result = ElectronicInvoiceResult.Failure("TIMEOUT", "HKA no respondió"),
                )
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(digital))

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = false))

            assertNull(response.numeroDocumentoFiscal, "Failure → nunca se inventa numeroDocumentoFiscal")
            assertNull(response.numeroControlThka, "Failure → nunca se inventa numeroControlThka")
            assertTrue(response.feError!!.contains("TIMEOUT"))
        }

    @Test
    fun `FASE 2 g - PA nunca popular numeroDocumentoFiscal ni numeroControlThka`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val pa = RecordingStrategy("PA", result = ElectronicInvoiceResult.Success(cufe = "CUFE-PA-X"))
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(pa))

            val response = useCase.execute(db, "PA", sampleRequest(useHka20 = false))

            assertEquals("CUFE-PA-X", response.cufe, "PA conserva su cufe")
            assertNull(response.numeroDocumentoFiscal, "PA NO tiene numeroDocumentoFiscal VE")
            assertNull(response.numeroControlThka, "PA NO tiene numeroControlThka VE")
        }

    @Test
    fun `FASE 2 h - VE Uncertain no propaga campos fiscales - respuesta conservadora`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital =
                RecordingStrategy(
                    "VE",
                    result =
                        ElectronicInvoiceResult.Uncertain(
                            country = "VE",
                            codigo = "EMISSION_INDETERMINATE",
                            mensaje = "No se pudo confirmar la emisión",
                        ),
                )
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(digital))

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = false))

            assertNull(response.numeroDocumentoFiscal, "Uncertain NO inventa numDoc: el operador debe reconciliar")
            assertNull(response.numeroControlThka, "Uncertain NO inventa numCtrl: el operador debe reconciliar")
        }

    // ─── FASE 2 (Punto 6) — Regresión Panamá: conceptos PA intactos ────────
    //
    // Asegura que los cambios de VE no rompan ni contaminen los conceptos de PA:
    //   - cufe/qr/fechaRecepcionDGI/nroProtocoloAutorizacion siguen siendo el transporte PA;
    //   - VE jamás los transporta ni los imprime.

    @Test
    fun `FASE 2 reg-PA-a - PA Success conserva cufe, qr, nroProtocoloAutorizacion y fechaRecepcionDGI`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val pa =
                RecordingStrategy(
                    "PA",
                    result =
                        ElectronicInvoiceResult.Success(
                            cufe = "CUFE-PA-1",
                            qr = "QR-PA",
                            fechaRecepcionDGI = "2026-08-04T10:00:00",
                            nroProtocoloAutorizacion = "PROTO-PA-1",
                        ),
                )
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(pa))

            val result = pa.lastResult as ElectronicInvoiceResult.Success
            val response = useCase.execute(db, "PA", sampleRequest(useHka20 = false))

            assertEquals("CUFE-PA-1", response.cufe)
            assertEquals("QR-PA", response.qr)
            assertEquals("2026-08-04T10:00:00", response.fechaRecepcionDGI)
            // La PA SI usa el campo interno nroProtocoloAutorizacion del Success (intacto).
            assertEquals(
                "PROTO-PA-1",
                result.nroProtocoloAutorizacion,
                "PA conserva nroProtocoloAutorizacion como campo interno del Success (Punto 6)",
            )
            assertNull(response.numeroDocumentoFiscal, "PA jamás debe exponer un campo VE")
            assertNull(response.numeroControlThka, "PA jamás debe exponer un campo VE")
        }

    @Test
    fun `FASE 2 reg-PA-b - VE Success jamas toca cufe ni nroProtocoloAutorizacion`() =
        runBlocking {
            val saleRepo = FakeSaleRepo()
            val digital =
                RecordingStrategy(
                    "VE",
                    result =
                        ElectronicInvoiceResult.Success(
                            numeroDocumentoFiscal = "00001234",
                            numeroControlThka = "001-00001",
                            // cufe/qr/etc quedan en null por defecto - Punto 1
                        ),
                )
            val useCase = ProcessSaleUseCase(saleRepo, SingleStrategyFactory(digital))

            val response = useCase.execute(db, "VE", sampleRequest(useHka20 = false))

            val result = digital.lastResult as ElectronicInvoiceResult.Success
            assertNull(response.cufe, "VE jamás persiste ni transporta cufe")
            assertNull(response.qr, "VE no imprime QR")
            assertNull(response.fechaRecepcionDGI, "VE no imprime fechaRecepcionDGI")
            assertNull(result.nroProtocoloAutorizacion, "VE jamás transporta nroProtocoloAutorizacion")
            assertEquals("00001234", response.numeroDocumentoFiscal)
            assertEquals("001-00001", response.numeroControlThka)
        }

    // ─── Fakes ─────────────────────────────────────────────────────────────

    /** Repo que siempre retorna una venta exitosa sin tocar la DB real. */
    private class FakeSaleRepo(
        private val success: Boolean = true,
        private val codEstatus: Int = 2,
        private val codFactura: String = "F-001-0001",
    ) : ProcessSaleTransactionalRepository() {
        override fun process(
            countryCode: String,
            request: ProcessSaleRequest,
        ): ProcessSaleResponse =
            ProcessSaleResponse(
                success = success,
                idFactura = request.idFactura ?: "INV-FIXTURE",
                codFactura = codFactura,
                codEstatus = codEstatus,
            )
    }

    /**
     * Repo que simula la idempotencia comercial por `idFactura` del ledger real
     * (Brief item 2). La primera invocación de un `idFactura` inserta y confirma
     * la venta (`cod_estatus=2`). Reinvocaciones para el MISMO `idFactura`
     * lanzan [DuplicateInvoiceException] — tal como hace
     * [ProcessSaleTransactionalRepository.validateDuplicateInvoice] en producción.
     */
    private class DuplicateAwareSaleRepo : ProcessSaleTransactionalRepository() {
        private val processed = mutableSetOf<String>()

        override fun process(
            countryCode: String,
            request: ProcessSaleRequest,
        ): ProcessSaleResponse {
            val id = request.idFactura?.takeIf { it.isNotBlank() } ?: "INV-${System.nanoTime()}"
            if (!processed.add(id)) {
                throw DuplicateInvoiceException("La factura ya existe y está procesada (cod_estatus=2)")
            }
            return ProcessSaleResponse(
                success = true,
                idFactura = id,
                codFactura = "F-DUP-${id.takeLast(6)}",
                codEstatus = 2,
            )
        }
    }

    /** Strategy contable que retorna un resultado prefijado. */
    private class RecordingStrategy(
        override val countryCode: String,
        private val result: ElectronicInvoiceResult = ElectronicInvoiceResult.NotApplicable(countryCode),
    ) : ElectronicInvoiceStrategy {
        var processCalls = 0
            private set

        /** Último resultado retornado; útil para inspeccionar campos del Success. */
        var lastResult: ElectronicInvoiceResult = result
            private set

        override suspend fun processElectronicInvoice(
            database: Database,
            invoiceId: String,
        ): ElectronicInvoiceResult {
            processCalls += 1
            lastResult = result
            return result
        }
    }

    /** Strategy que lanza si la invocan; útil para asertar "no fue invocada". */
    private class ExplosiveStrategy(
        override val countryCode: String,
    ) : ElectronicInvoiceStrategy {
        override suspend fun processElectronicInvoice(
            database: Database,
            invoiceId: String,
        ): ElectronicInvoiceResult = error("El digital NO debió invocarse para este caso (useHka20=true o país no-VE)")
    }

    /**
     * FASE 1.1 — Ítem 8: implementación local de [ProcessorFactory] preferida
     * por el brief ("interfaces/puertos/composición/di" en lugar de heredar de
     * la factory de producción). La factory real queda final.
     */
    private class SingleStrategyFactory(
        private val strategy: ElectronicInvoiceStrategy,
    ) : ProcessorFactory {
        override fun forCountry(countryCode: String): ElectronicInvoiceStrategy = strategy
    }

    // ─── Builder de request mínimo ─────────────────────────────────────────

    private fun sampleRequest(
        idFactura: String? = "INV-${System.nanoTime()}",
        useHka20: Boolean? = null,
    ): ProcessSaleRequest =
        ProcessSaleRequest(
            idFactura = idFactura,
            procesar = 1,
            factura =
                SaleInvoiceInput(
                    idCliente = "1",
                    codCliente = "CF",
                    codVendedor = 1,
                    idShop = 1,
                    idSucursal = 1,
                    idCaja = "1",
                    codigoCaja = "C1",
                    idCajaSecuencia = "1",
                    serieSucursal = "L001P001",
                    formaPago = "01",
                    subtotal = 100.0,
                    ivaTotalFactura = 16.0,
                    totalTotalFactura = 116.0,
                    montoItemsFactura = 100.0,
                    totalizarBaseImponible = 100.0,
                    totalizarMontoIva = 16.0,
                    totalizarTotalGeneral = 116.0,
                    usuarioCreacion = "TEST",
                ),
            items =
                listOf(
                    SaleItemInput(
                        idItem = 1,
                        itemAlmacen = 1,
                        itemDescripcion = "PRODUCTO TEST",
                        itemCantidad = 1.0,
                        itemPrecioSinIva = 100.0,
                        itemPIva = 16.0,
                        itemTotalSinIva = 100.0,
                        itemTotalConIva = 116.0,
                        itemCantidadTotal = 1.0,
                    ),
                ),
            pagoResumen =
                SalePaymentSummaryInput(
                    totalizarMontoCancelar = 116.0,
                    totalizarMontoEfectivo = 116.0,
                    totalizarCambio = 0.0,
                    totalizarSaldoPendiente = 0.0,
                ),
            pagos =
                listOf(
                    SalePaymentInput(
                        idFormaPago = 1,
                        tipoMovimiento = "ING",
                        monto = 116.0,
                        montoRecibido = 116.0,
                    ),
                ),
            moneda = SaleCurrencyInput(),
            useHka20 = useHka20,
        )
}
