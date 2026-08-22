package com.amaxonia.pos.domain.model.contract

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.dto.LoginRequest
import com.amaxonia.pos.data.remote.dto.LoginResponse
import com.amaxonia.pos.data.remote.dto.SelectCompanyRequest
import com.amaxonia.pos.data.remote.dto.SelectCompanyResponse
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteRequestDto
import com.amaxonia.pos.domain.model.mesas.AbrirSesionRequest
import com.amaxonia.pos.domain.model.mesas.CrearCuentaRequest
import com.amaxonia.pos.domain.model.mesas.CrearPedidoMesaRequest
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Tests de contrato cross-system (FASE 9 / TASK-091): cada fixture en
 * `contracts/` (fuente única en la raíz del repo) debe decodificar en el DTO
 * Android y re-serializar al MISMO JSON (round-trip element-equality).
 *
 * Las respuestas producidas por el backend se decodifican tolerando claves
 * extra (`countryCode`/`schemaType`) y se asercan los campos que Android usa.
 */
class ContractFixtureTest {
    private fun contractsRoot(): Path {
        var dir: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        repeat(6) {
            val current = dir ?: return@repeat
            val candidate = current.resolve("contracts")
            if (Files.isDirectory(candidate)) return candidate
            dir = current.parent
        }
        error("No se encontró contracts/ desde ${System.getProperty("user.dir")}")
    }

    private fun fixture(relPath: String): String = String(Files.readAllBytes(contractsRoot().resolve(relPath)), Charsets.UTF_8)

    /** Respuestas producidas por el backend: decodificación tolerante, SIN round-trip. */
    private fun <T> decode(
        relPath: String,
        serializer: KSerializer<T>,
    ): T = AppJson.decodeFromString(serializer, fixture(relPath))

    private fun <T> roundTrip(
        relPath: String,
        serializer: KSerializer<T>,
        assertDecoded: (T) -> Unit = {},
    ): T {
        val raw = fixture(relPath)
        val decoded = AppJson.decodeFromString(serializer, raw)
        assertDecoded(decoded)
        val reEncoded = AppJson.parseToJsonElement(AppJson.encodeToString(serializer, decoded))
        assertEquals(
            "Round-trip cambió el contrato: $relPath",
            AppJson.parseToJsonElement(raw),
            reEncoded,
        )
        return decoded
    }

    // ─── Auth ──────────────────────────────────────────────────────────────

    @Test
    fun `auth fixtures mantienen el contrato`() {
        roundTrip("auth/login-request.json", LoginRequest.serializer()) {
            assertEquals("cajero1", it.username)
        }
        val login = decode("auth/login-response.json", LoginResponse.serializer())
        assertEquals(7, login.user.id)
        assertEquals("TEST-ID", login.companies.single().rif)
        assertTrue("el token identity es obligatorio", login.token.isNotBlank())
        roundTrip("auth/company-select-request.json", SelectCompanyRequest.serializer()) {
            assertEquals(2, it.companyId)
        }
        val company = decode("auth/company-select-response.json", SelectCompanyResponse.serializer())
        assertEquals("t_prueba", company.currentCompany.adminDb)
    }

    // ─── Sales ─────────────────────────────────────────────────────────────

    @Test
    fun `sale request contado mantiene el contrato`() {
        roundTrip("sale/process-sale-request.json", ProcessSaleRequestDto.serializer()) {
            assertEquals("contado", it.factura.formaPago)
            assertEquals(0.0, it.pagoResumen.totalizarSaldoPendiente, 0.0)
        }
    }

    @Test
    fun `credit sale request mantiene el contrato y los disparadores de credito`() {
        roundTrip("sale/credit-sale-request.json", ProcessSaleRequestDto.serializer()) {
            assertEquals("credito", it.factura.formaPago)
            assertEquals(10.44, it.pagoResumen.totalizarSaldoPendiente, 0.0)
            assertEquals("CXC", it.pagos.single().tipoMovimiento)
        }
    }

    @Test
    fun `partial credit collection request mantiene el contrato`() {
        roundTrip(
            "sale/partial-credit-collection-request.json",
            ProcessSaleRequestDto.serializer(),
        ) { request ->
            assertTrue("la bandera debe serializarse (true != default)", request.esCobroCreditoPrevio)
            assertEquals("FAC-ORIGINAL-001", request.idFactura)
            assertTrue(request.items.isEmpty())
            assertEquals(0.0, request.pagoResumen.totalizarSaldoPendiente, 0.0)
        }
    }

    @Test
    fun `process sale response del backend decodifica con campos PA`() {
        val response =
            AppJson.decodeFromString(ProcessSaleResponseDto.serializer(), fixture("sale/process-sale-response.json"))
        assertEquals(true, response.success)
        assertEquals("offline-id-001", response.idFactura)
        assertEquals("FAC-000123", response.codFactura)
        assertEquals(2, response.codEstatus)
        assertEquals("CUFE-123", response.cufe)
    }

    @Test
    fun `respuestas VE digital y HKA-20 del backend decodifican con campos fiscales`() {
        // Espejo de MultiCountryContractMatrixTest: los fixtures VE comparten el
        // DTO con PA; el país se discrimina por presencia/ausencia de campos.
        listOf(
            "sale/ve-digital-process-sale-response.json",
            "sale/ve-hka20-process-sale-response.json",
        ).forEach { relPath ->
            val response = decode(relPath, ProcessSaleResponseDto.serializer())
            assertTrue("cufe debe ausentarse en VE: $relPath", response.cufe == null)
            assertTrue(
                "documento fiscal VE obligatorio: $relPath",
                !response.numeroDocumentoFiscal.isNullOrBlank(),
            )
            assertTrue(
                "control HKA obligatorio: $relPath",
                !response.numeroControlThka.isNullOrBlank(),
            )
        }
    }

    // ─── Caja ──────────────────────────────────────────────────────────────

    @Test
    fun `caja fixtures mantienen el contrato`() {
        roundTrip("caja/caja-apertura-request.json", AperturaRequest.serializer()) {
            assertEquals(50.0, it.montoApertura, 0.0)
        }
        roundTrip("caja/caja-cierre-request.json", CierreCajaRequest.serializer()) { cierre ->
            assertEquals(172.0, cierre.monto_total, 0.0)
            assertEquals(1, cierre.detalle_formapago.size)
            assertEquals(107.0, cierre.detalle_formapago.single().monto, 0.0)
        }
    }

    // ─── Mesas ─────────────────────────────────────────────────────────────

    @Test
    fun `mesas fixtures mantienen el contrato`() {
        roundTrip("mesas/mesa-abrir-sesion-request.json", AbrirSesionRequest.serializer()) {
            assertEquals(2, it.cantidadPersonas)
        }
        roundTrip("mesas/mesa-crear-pedido-request.json", CrearPedidoMesaRequest.serializer()) {
            assertTrue(it.enviarInmediato)
            assertEquals("PRODUCTO", it.items.single().itemDescripcion)
        }
        roundTrip("mesas/mesa-crear-cuenta-request.json", CrearCuentaRequest.serializer()) {
            assertEquals(101, it.items.single().pedidoMesaId)
            assertEquals(false, it.incluirTodoPendiente)
        }
    }

    // ─── Credit notes ──────────────────────────────────────────────────────

    @Test
    fun `credit note fixtures mantienen el contrato`() {
        roundTrip("creditnote/create-credit-note-request.json", CreateCreditNoteRequestDto.serializer()) {
            assertEquals("REINTEGRO", it.settlementType.name)
        }
        roundTrip(
            "creditnote/confirm-credit-note-fiscal-request.json",
            ConfirmCreditNoteFiscalRequestDto.serializer(),
        ) {
            assertEquals("NC-000001", it.codDevolucionFiscal)
        }
    }

    // ─── Fiscal confirmation ───────────────────────────────────────────────

    @Test
    fun `fiscal confirmation request mantiene el contrato`() {
        roundTrip("fiscal/confirm-factura-fiscal-request.json", ConfirmFacturaFiscalRequestDto.serializer()) {
            assertEquals("00000000000000000001", it.numeroDocumentoFiscal)
            assertEquals("SN-PRN-7", it.impresoraSerial)
        }
    }
}
