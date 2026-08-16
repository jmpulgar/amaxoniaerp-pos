package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceVEContext
import com.amaxoniaerp.features.electronicinvoice.domain.VECajaData
import com.amaxoniaerp.features.electronicinvoice.domain.VECompradorData
import com.amaxoniaerp.features.electronicinvoice.domain.VEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.VECorrelativoReservado
import com.amaxoniaerp.features.electronicinvoice.domain.VEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.VEFacturaData
import com.amaxoniaerp.features.electronicinvoice.domain.VEFormaPagoData
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.sql.Connection

/**
 * Repositorio FE Venezuela con ciclo de vida:
 *
 *  - `loadInvoiceContext`: lee factura + cliente + detalle + formasPago + caja
 *    + parametros_generales para armar el contexto que el Builder consume.
 *  - `loadAlreadyIssued`: recarga la factura SIN JOINs para verificar
 *    idempotencia (numeroDocumentoFiscal ya presente → AlreadyIssued).
 *  - `reserveCorrelativoFacturaElectronica`: reserva ATÓMICA del número fiscal
 *    en su propia transacción, con bloqueo por id de la fila real. La
 *    transacción se COMPROMETE y CIERRA antes de llamar a HKA.
 *  - `releaseCorrelativo`: opcional, en caso que se quiera "devolver" el número
 *    reservado cuando no se puede emitir. NO se invoca automáticamente tras
 *    timeout (preferimos un salto de numeración a un documento duplicado).
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
            val reservado = VECorrelativoReservado(0, 8) // placeholder, lo asigna stratégie
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
     * indica una emisión confirmada whose número control se perdió/no llegó
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
     * Reemplaza al antiguo `reserveCorrelativoFacturaElectronica(database)`:
     * ahora la reserva acepta un [minimumNextNumber] que se calcula DESPUÉS de
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
            val candidatos =
                VECorrelativosTable
                    .selectAll()
                    .where { VECorrelativosTable.campo eq campoCorrelativoFe }
                    .toList()

            if (candidatos.isEmpty()) {
                throw FEConfigurationException(
                    "Falta fila en `correlativos` con campo='$campoCorrelativoFe'. No se puede reservar correlativo FE Venezuela.",
                )
            }
            if (candidatos.size > 1) {
                throw FEConfigurationException(
                    "Existen ${candidatos.size} filas en `correlativos` con campo='$campoCorrelativoFe'. Se esperaba exactamente una.",
                )
            }
            val fila = candidatos.single()
            val idFila = fila[VECorrelativosTable.id]
            val contadorActual = fila[VECorrelativosTable.contador]
            val formato = fila[VECorrelativosTable.formato] ?: DEFAULT_CORRELATIVO_FORMAT

            // Bloquear la fila real por su id (para FOR UPDATE específico).
            VECorrelativosTable
                .select(VECorrelativosTable.id, VECorrelativosTable.contador)
                .where { VECorrelativosTable.id eq idFila }
                .forUpdate()
                .single()

            // Item 3: reservado = max(contadorActual, minimumNextNumber). El contador
            // se sincroniza con el mínimo para que la próxima reserva no colisione.
            val reservado =
                VECorrelativoReservado(
                    numero = maxOf(contadorActual, minimumNextNumber),
                    formato = formato,
                )
            val updated =
                VECorrelativosTable.update({ VECorrelativosTable.id eq idFila }) {
                    with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                        it[VECorrelativosTable.contador] = reservado.numero + 1
                    }
                }
            if (updated != 1) {
                throw FEConfigurationException(
                    "No se pudo actualizar la fila de correlativo id=$idFila (updated=$updated). No se reservó ningún número.",
                )
            }
            log.info(
                "[VE-FE] reserveAtLeast reservado numero={} formato={} (minimumNextNumber={} contadorActual={} proximo={})",
                reservado.numeroFormateado(),
                formato,
                minimumNextNumber,
                contadorActual,
                reservado.numero + 1,
            )
            reservado
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
     *     (solo si `codFacturaFiscalColumnaExiste=true`; verificación por Tenant).
     *   - factura.numero_control_thka     = resultado.numeroControl
     *
     * NO se tocan `cufe`, `qr`, `fechaRecepcionDGI`, etc. Esos son de Panamá.
     *
     * No se persiste serie, imprentaDigital, autorizado, fechaAsignacion ni
     * transaccionId: si se requieren, deben alojarse en una columna dedicada
     * en una migración posterior (no en FASE 1).
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

    // ─── Interno: lecturas ───────────────────────────────────────────────────

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

    private data class FacturaCargada(
        val facturaData: VEFacturaData,
        val idCaja: String,
        val idSucursal: Int,
        val idClienteComprador: String?,
    )

    private fun loadFactura(invoiceId: String): FacturaCargada {
        val row =
            VEFacturaReadTable
                .selectAll()
                .where { VEFacturaReadTable.idFactura eq invoiceId }
                .limit(1)
                .firstOrNull()
                ?: throw FEInvoiceNotFoundException("Factura no encontrada: $invoiceId")

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
     *
     * Antes la URL se leía de `parametros_generales.api_thefactoryhka`, columna
     * que NO forma parte del esquema real del tenant. El operador sólo debe
     * escoger el entorno numérico; las URLs canónicas viven en el código.
     */
    private fun baseUrlParaEntorno(tipoEntorno: Int): String =
        when (tipoEntorno) {
            ENV_DEMO -> URL_BASE_DEMO
            ENV_PROD -> URL_BASE_PROD
            else -> throw FEConfigurationException(
                "tipo_entorno_ve=$tipoEntorno inválido. Use 0=demo, 1=producción.",
            )
        }

    private fun loadConfig(): VEConfigData {
        val row =
            VEParametrosReadTable
                .selectAll()
                .orderBy(VEParametrosReadTable.codEmpresa)
                .limit(1)
                .firstOrNull()
                ?: throw FEConfigurationException("parametros_generales sin filas para FE VE")

        val tipoFact = row[VEParametrosReadTable.tipoFacturacion]
        val tokenE =
            row[VEParametrosReadTable.tokenEmpresa]
                ?: throw FEConfigurationException("Falta token_empresa en parametros_generales (FE VE)")
        val tokenP =
            row[VEParametrosReadTable.tokenPassword]
                ?: throw FEConfigurationException("Falta token_password en parametros_generales (FE VE)")
        val tipoEntorno = row[VEParametrosReadTable.tipoEntornoVe]
        val rif =
            row[VEParametrosReadTable.rif]
                ?: throw FEConfigurationException("Falta rif en parametros_generales (FE VE)")

        // FASE 1.1 (cleanup): baseUrl se deriva por configuración de aplicación
        // desde `tipo_entorno_ve` (0=demo, 1=producción). NO se lee de la base
        // del tenant porque la columna `api_thefactoryhka` NO existe en el
        // esquema real. Ver [baseUrlParaEntorno].
        val baseUrl = baseUrlParaEntorno(tipoEntorno)

        // Log sin secretos.
        log.info(
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

    private fun loadComprador(
        idClienteComprador: String?,
        factura: VEFacturaData,
    ): VECompradorData {
        if (idClienteComprador.isNullOrBlank()) {
            // Sin cliente asociado: usar facturar_a* como fallback nominal.
            return VECompradorData(
                nombreRazonSocial = factura.facturarANombre.ifBlank { "CONSUMIDOR FINAL" },
                rif = factura.facturarARuc.ifBlank { "V000000000" },
                direccion = factura.facturarADireccion.takeIf { it.isNotBlank() },
                telefono = factura.facturarATelefono.takeIf { it.isNotBlank() },
                email = null,
            )
        }
        val row =
            VEClientesReadTable
                .selectAll()
                .where { VEClientesReadTable.idCliente eq idClienteComprador }
                .limit(1)
                .firstOrNull()
        return if (row == null) {
            VECompradorData(
                nombreRazonSocial = factura.facturarANombre.ifBlank { "CONSUMIDOR FINAL" },
                rif = factura.facturarARuc.ifBlank { "V000000000" },
                direccion = factura.facturarADireccion.takeIf { it.isNotBlank() },
                telefono = factura.facturarATelefono.takeIf { it.isNotBlank() },
                email = null,
            )
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

    private fun loadDetalles(invoiceId: String): List<VEDetalleData> =
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
     * (divisa) tienen `tipo_moneda='D'` (u otro distinto de la base). El cálculo
     * exacto de `esDivisa` depende de la convención del tenant; aquí se asume
     * que `tipo_moneda != abr_moneda_base` es divisa.
     */
    private fun loadFormasPago(
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
                    log.warn("[VE-FE] No se encontró caja_nueva para invoiceId={}", invoiceId)
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

    private fun loadCaja(
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

    companion object {
        private const val DEFAULT_CORRELATIVO_FORMAT = 8

        // ─── Entornos The Factory HKA Venezuela (FASE 1.1 cleanup) ─────────
        // La URL base del PAC NO se persiste en la base del tenant. Se deriva
        // por configuración de aplicación desde `parametros_generales.tipo_entorno_ve`.

        /** Código de entorno demo en `parametros_generales.tipo_entorno_ve`. */
        private const val ENV_DEMO = 0

        /** Código de entorno producción en `parametros_generales.tipo_entorno_ve`. */
        private const val ENV_PROD = 1

        /** URL base The Factory HKA Venezuela — demo. */
        private const val URL_BASE_DEMO = "https://demoemision.thefactoryhka.com.ve/api"

        /** URL base The Factory HKA Venezuela — producción. */
        private const val URL_BASE_PROD = "https://emision.thefactoryhka.com.ve/api"
    }
}
