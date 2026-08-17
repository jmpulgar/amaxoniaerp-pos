package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceVEContext
import com.amaxoniaerp.features.electronicinvoice.domain.VECorrelativoReservado
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.sql.Connection

private const val PLACEHOLDER_FISCAL_NUMBER_LENGTH = 8
private const val DEFAULT_CORRELATIVO_FORMAT = 8

/**
 * Repositorio FE Venezuela con ciclo de vida:
 *
 *  - `loadInvoiceContext`: lee factura + cliente + detalle + formasPago + caja
 *    + parametros_generales para armar el contexto que el Builder consume
 *    (lecturas en VenezuelaInvoiceContextLoader.kt).
 *  - `loadAlreadyIssued`: recarga la factura SIN JOINs para verificar
 *    idempotencia (numeroDocumentoFiscal ya presente → AlreadyIssued).
 *  - `reserveAtLeast`: reserva ATÓMICA del número fiscal en su propia
 *    transacción, con bloqueo por id de la fila real. La transacción se
 *    COMPROMETE y CIERRA antes de llamar a HKA.
 *  - `updateInvoiceWithVEResult`: persiste SOLO los tres campos de resultado
 *    fiscal en su propia transacción.
 *
 * Clase `open` para permitir fakes en tests sin tocar la DB.
 */
open class VenezuelaElectronicInvoiceRepository {
    private val log = LoggerFactory.getLogger(VenezuelaElectronicInvoiceRepository::class.java)

    /** Campo de `correlativos` que indica el contador de FE Venezuela. */
    val campoCorrelativoFe = "correlativo_factura_electronica"

    /**
     * Carga el contexto completo de la factura para enviarla a HKA Venezuela.
     */
    open suspend fun loadInvoiceContext(
        database: Database,
        invoiceId: String,
    ): InvoiceVEContext =
        dbQuery(database) {
            val factura = loadFactura(invoiceId)
            val config = loadConfig()
            val comprador = loadComprador(factura.idClienteComprador, factura.facturaData)
            val detalles = loadDetalles(invoiceId)
            val formasPago = loadFormasPago(factura.idCaja, invoiceId)
            val caja = loadCaja(factura.idCaja, factura.idSucursal, config)
            // placeholder, lo asigna la strategy
            val reservado = VECorrelativoReservado(0, PLACEHOLDER_FISCAL_NUMBER_LENGTH)
            InvoiceVEContext(
                config = config,
                factura = factura.facturaData,
                comprador = comprador,
                detalles = detalles,
                formasPago = formasPago,
                caja = caja,
                correlativoReservado = reservado,
            )
        }

    /**
     * Verifica idempotencia con semántica OR (FASE 1.1 — Brief item 1).
     *
     * Basta con que UNO de los dos campos fiscales esté presente para
     * considerar que la factura YA fue procesada por el PAC. En VE, una
     * factura con `numeroDocumentoFiscal` SÓLO (sin `numero_control_thka`)
     * indica una emisión confirmada cuyo número control se perdió/no llegó
     * — reemitir generaría un duplicado. Lo simétrico aplica.
     *
     * Casos cubiertos:
     *   - ambos presentes                   → [AlreadyIssuedResult.Complete]
     *   - sólo `numeroDocumentoFiscal`      → [AlreadyIssuedResult.Partial]
     *   - sólo `numero_control_thka`        → [AlreadyIssuedResult.Partial]
     *   - ambos ausentes                    → [AlreadyIssuedResult.None]
     *
     * En los tres primeros **NO se debe llamar al PAC**. La strategy retorna
     * `AlreadyIssued` (Complete) o un `Failure(PARTIAL_FISCAL_DATA)` para
     * Partial, indicando la necesidad de reconciliación manual.
     */
    open suspend fun loadAlreadyIssued(
        database: Database,
        invoiceId: String,
    ): AlreadyIssuedResult =
        dbQuery(database) {
            val row =
                VEFacturaReadTable
                    .select(VEFacturaReadTable.numeroDocumentoFiscal, VEFacturaReadTable.numeroControlThka)
                    .where { VEFacturaReadTable.idFactura eq invoiceId }
                    .limit(1)
                    .firstOrNull()
                    ?: throw FEInvoiceNotFoundException("Factura no encontrada: $invoiceId")
            val num = row[VEFacturaReadTable.numeroDocumentoFiscal]?.takeIf { it.isNotBlank() }
            val ctrl = row[VEFacturaReadTable.numeroControlThka]?.takeIf { it.isNotBlank() }
            when {
                num != null && ctrl != null ->
                    AlreadyIssuedResult.Complete(
                        numeroDocumentoFiscal = num,
                        numeroControl = ctrl,
                    )
                num != null ->
                    AlreadyIssuedResult.Partial(
                        numeroDocumentoFiscal = num,
                        numeroControl = null,
                    )
                ctrl != null ->
                    AlreadyIssuedResult.Partial(
                        numeroDocumentoFiscal = null,
                        numeroControl = ctrl,
                    )
                else -> AlreadyIssuedResult.None
            }
        }

    /**
     * FASE 2 (Punto 5) — Recarga idempotente de los campos fiscales persistidos.
     *
     * Tras una emisión exitosa o en un reintento/reimpresión, devolver al POS
     * los valores efectivamente guardados en `factura` (fuente de verdad) y NO
     * el objeto inmediato retornado por HKA. Esto garantiza:
     *  - que la respuesta comercial refleje exactamente lo persistido;
     *  - que la reimpresión (SuccessScreen.onPrintReceipt / print-payload) jamás
     *    vuelva a llamar Autenticación / UltimoDocumento / Emision.
     *
     * Es **read-only**: no llama al PAC, no muta, no reserva correlativo.
     */
    open suspend fun loadFiscalDataForResponse(
        database: Database,
        invoiceId: String,
    ): FiscalSnapshot =
        dbQuery(database) {
            val row =
                VEFacturaReadTable
                    .select(VEFacturaReadTable.numeroDocumentoFiscal, VEFacturaReadTable.numeroControlThka)
                    .where { VEFacturaReadTable.idFactura eq invoiceId }
                    .limit(1)
                    .firstOrNull()
                    ?: return@dbQuery FiscalSnapshot(null, null)
            FiscalSnapshot(
                numeroDocumentoFiscal = row[VEFacturaReadTable.numeroDocumentoFiscal]?.takeIf { it.isNotBlank() },
                numeroControlThka = row[VEFacturaReadTable.numeroControlThka]?.takeIf { it.isNotBlank() },
            )
        }

    /** Snapshot read-only de los campos fiscales Venezuela persistidos. */
    data class FiscalSnapshot(
        val numeroDocumentoFiscal: String?,
        val numeroControlThka: String?,
    )

    /**
     * Reserva atómica del correlativo fiscal Venezuela con mínimo garantizado
     * (FASE 1.1 — Brief item 3 / `reserveAtLeast`).
     *
     * La reserva acepta un [minimumNextNumber] que se calcula DESPUÉS de
     * consultar `UltimoDocumento` al PAC (remoto + 1) pero ANTES de abrir la
     * transacción SQL. Bajo contención el contador saltará automáticamente al
     * mínimo, evitando colisión con el PAC. NO se mantiene el patrón `max()`
     * en la Strategy.
     *
     * Pasos (según brief, transacción breve y AUTOCONTENIDA — nunca abierta
     * durante HTTP):
     *   1. Abrir nueva transacción REPEATABLE_READ.
     *   2. Seleccionar fila(s) con `campo = 'correlativo_factura_electronica'`.
     *   3. Fallar controladamente si no existe EXACTAMENTE una.
     *   4. FOR UPDATE del registro por su `id` (bloqueo pesimista a la fila real).
     *   5. computar `reservado = max(contadorActual, minimumNextNumber)`.
     *   6. Update: `contador = reservado + 1`.
     *   7. COMMIT (la transacción se cierra al regresar del bloque).
     *
     * Concurrency: dos llamadas concurrentes con el mismo `minimumNextNumber`
     * NUNCA reciben el mismo `numero` — el `FOR UPDATE` serializa el acceso a
     * la fila; el segundo en adquirir el lock lee el `contador` ya avanzado.
     */
    open suspend fun reserveAtLeast(
        database: Database,
        minimumNextNumber: Int = 1,
    ): VECorrelativoReservado {
        require(minimumNextNumber >= 1) { "minimumNextNumber debe ser >= 1 (recibido: $minimumNextNumber)" }
        return transaction(
            transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ,
            db = database,
        ) {
            val (idFila, contadorActual, formato) = unicoCandidatoCorrelativo()
            bloquearFilaCorrelativo(idFila)

            // Item 3: reservado = max(contadorActual, minimumNextNumber). El contador
            // se sincroniza con el mínimo para que la próxima reserva no colisione.
            val reservado =
                VECorrelativoReservado(
                    numero = maxOf(contadorActual, minimumNextNumber),
                    formato = formato,
                )
            actualizarContadorCorrelativo(idFila, reservado.numero + 1)
            log.info(
                "[VE-FE] reserveAtLeast reservado numero={} formato={} " +
                    "(minimumNextNumber={} contadorActual={} proximo={})",
                reservado.numeroFormateado(),
                formato,
                minimumNextNumber,
                contadorActual,
                reservado.numero + 1,
            )
            reservado
        }
    }

    private data class CorrelativoRow(
        val id: Int,
        val contador: Int,
        val formato: Int,
    )

    private fun unicoCandidatoCorrelativo(): CorrelativoRow {
        val candidatos =
            VECorrelativosTable
                .selectAll()
                .where { VECorrelativosTable.campo eq campoCorrelativoFe }
                .toList()

        if (candidatos.isEmpty()) {
            throw FEConfigurationException(
                "Falta fila en `correlativos` con campo='$campoCorrelativoFe'. " +
                    "No se puede reservar correlativo FE Venezuela.",
            )
        }
        if (candidatos.size > 1) {
            throw FEConfigurationException(
                "Existen ${candidatos.size} filas en `correlativos` con campo='$campoCorrelativoFe'. " +
                    "Se esperaba exactamente una.",
            )
        }
        val fila = candidatos.single()
        return CorrelativoRow(
            id = fila[VECorrelativosTable.id],
            contador = fila[VECorrelativosTable.contador],
            formato = fila[VECorrelativosTable.formato] ?: DEFAULT_CORRELATIVO_FORMAT,
        )
    }

    // Bloquear la fila real por su id (para FOR UPDATE específico).
    private fun bloquearFilaCorrelativo(idFila: Int) {
        VECorrelativosTable
            .select(VECorrelativosTable.id, VECorrelativosTable.contador)
            .where { VECorrelativosTable.id eq idFila }
            .forUpdate()
            .single()
    }

    private fun actualizarContadorCorrelativo(
        idFila: Int,
        proximoContador: Int,
    ) {
        val updated =
            VECorrelativosTable.update({ VECorrelativosTable.id eq idFila }) {
                with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                    it[VECorrelativosTable.contador] = proximoContador
                }
            }
        if (updated != 1) {
            throw FEConfigurationException(
                "No se pudo actualizar la fila de correlativo id=$idFila (updated=$updated). " +
                    "No se reservó ningún número.",
            )
        }
    }

    /**
     * @deprecated mantenido para compatibilidad con tests existentes; usa
     * [reserveAtLeast] en producción. Equivale a `reserveAtLeast(db, 1)`.
     */
    open suspend fun reserveCorrelativoFacturaElectronica(database: Database): VECorrelativoReservado =
        reserveAtLeast(database, minimumNextNumber = 1)

    /**
     * Persiste ATÓMICAMENTE el resultado fiscal exitoso en `factura`.
     *
     * Campos escritos (FASE 1, EXACTAMENTE estos tres):
     *   - factura.numeroDocumentoFiscal   = resultado.numeroDocumento
     *   - factura.cod_factura_fiscal      = resultado.numeroDocumento
     *   - factura.numero_control_thka     = resultado.numeroControl
     *
     * NO se tocan `cufe`, `qr`, `fechaRecepcionDGI`, etc. Esos son de Panamá.
     */
    open suspend fun updateInvoiceWithVEResult(
        database: Database,
        invoiceId: String,
        numeroDocumento: String,
        numeroControl: String,
    ) = dbQuery(database) {
        val updated =
            VEFacturaReadTable.update({ VEFacturaReadTable.idFactura eq invoiceId }) {
                // Cod_factura fiscal se actualiza al mismo valor que numeroDocumento.
                // El campo existe en VE y PA (ver BaseSalesFacturaTable.codFacturaFiscal).
                it[VEFacturaReadTable.codFacturaFiscal] = numeroDocumento
                it[VEFacturaReadTable.numeroDocumentoFiscal] = numeroDocumento
                it[VEFacturaReadTable.numeroControlThka] = numeroControl
            }
        if (updated != 1) {
            log.warn(
                "[VE-FE] updateInvoiceWithVEResult afectó {} filas, se esperaba 1. invoiceId={}",
                updated,
                invoiceId,
            )
        }
    }

    // ─── Interno: resultados ──────────────────────────────────────────────────

    /**
     * Resultado de idempotencia con semántica OR (FASE 1.1).
     *
     * - [Complete]: ambos campos fiscales presentes.
     * - [Partial]:  exactamente uno presente → NO se debe reemitir.
     * - [None]:     ninguno presente → continuar con el flujo.
     */
    sealed class AlreadyIssuedResult {
        /** Ninguno de los dos campos está presente → continuar con la emisión. */
        object None : AlreadyIssuedResult()

        /** ambos campos fiscales ya persistidos → ya emitida. */
        data class Complete(
            val numeroDocumentoFiscal: String,
            val numeroControl: String?,
        ) : AlreadyIssuedResult()

        /**
         * Exactamente uno de los dos campos persistido → NO reprocesar.
         * Requiere reconciliación manual (no se puede asumir cuál es el válido).
         */
        data class Partial(
            val numeroDocumentoFiscal: String?,
            val numeroControl: String?,
        ) : AlreadyIssuedResult()
    }
}
