package com.amaxoniaerp

import com.amaxoniaerp.features.auth.domain.LoginRequest
import com.amaxoniaerp.features.auth.domain.LoginResponse
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.companies.domain.CompanySelectRequest
import com.amaxoniaerp.features.companies.domain.CompanySelectResponse
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.facturas.domain.ConfirmFacturaFiscalRequest
import com.amaxoniaerp.features.mesas.domain.AbrirSesionRequest
import com.amaxoniaerp.features.mesas.domain.CrearCuentaRequest
import com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de contrato cross-system (FASE 9 / TASK-092): los MISMOS fixtures de
 * `contracts/` que consume Android se decodifican aquí en los DTO del backend
 * y re-serializan al MISMO JSON. Cualquier divergencia Android/backend rompe
 * estos tests o sus espejos en el lado POS.
 */
class ContractFixtureTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

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

    private fun <T> decodeAssert(
        relPath: String,
        serializer: KSerializer<T>,
        assertDecoded: (T) -> Unit,
    ) {
        val raw = Files.readString(contractsRoot().resolve(relPath))
        val decoded = json.decodeFromString(serializer, raw)
        assertDecoded(decoded)
    }

    private fun <T> roundTrip(
        relPath: String,
        serializer: KSerializer<T>,
        assertDecoded: (T) -> Unit = {},
    ) {
        val raw = Files.readString(contractsRoot().resolve(relPath))
        val decoded = json.decodeFromString(serializer, raw)
        assertDecoded(decoded)
        // kotlin.test: assertEquals(expected, actual, mensaje)
        assertEquals(
            json.parseToJsonElement(raw),
            json.parseToJsonElement(json.encodeToString(serializer, decoded)),
            "Round-trip cambió el contrato: $relPath",
        )
    }

    @Test
    fun `auth fixtures mantienen el contrato`() {
        roundTrip("auth/login-request.json", LoginRequest.serializer()) {
            assertEquals("cajero1", it.username)
        }
        roundTrip("auth/login-response.json", LoginResponse.serializer()) { login ->
            assertEquals("PA", login.countryCode)
            assertEquals("TYPE_A", login.schemaType)
            assertEquals("CAJERO", login.user.role)
        }
        roundTrip("auth/company-select-request.json", CompanySelectRequest.serializer()) {
            assertEquals(2, it.companyId)
        }
        roundTrip("auth/company-select-response.json", CompanySelectResponse.serializer()) { selected ->
            assertTrue(selected.success)
            assertEquals("t_prueba", selected.currentCompany.adminDb)
            assertEquals("nom_prueba", selected.currentCompany.payrollDb)
        }
    }

    @Test
    fun `sale request contado mantiene el contrato`() {
        // Decode-assert (sin round-trip): Android envía "codEstatus":2 porque es
        // requerido en su DTO; en backend es default y encodeDefaults=false lo
        // omite al re-encodar. El cable Android→backend es correcto tal cual.
        decodeAssert("sale/process-sale-request.json", ProcessSaleRequest.serializer()) {
            assertEquals("contado", it.factura.formaPago)
            assertEquals(0.0, it.pagoResumen.totalizarSaldoPendiente, 0.0)
            assertEquals(2, it.factura.codEstatus)
            assertEquals(null, it.useHka20)
        }
    }

    @Test
    fun `credit sale request mantiene los disparadores de credito`() {
        decodeAssert("sale/credit-sale-request.json", ProcessSaleRequest.serializer()) {
            assertEquals("credito", it.factura.formaPago)
            assertEquals("CXC", it.pagos.single().tipoMovimiento)
            assertTrue(it.pagoResumen.totalizarSaldoPendiente > 0.0)
        }
    }

    @Test
    fun `partial credit collection request mantiene el contrato`() {
        decodeAssert("sale/partial-credit-collection-request.json", ProcessSaleRequest.serializer()) { request ->
            assertEquals(true, request.esCobroCreditoPrevio)
            assertEquals("FAC-ORIGINAL-001", request.idFactura)
            assertTrue(request.items.isEmpty())
        }
    }

    @Test
    fun `process sale response mantiene el contrato`() {
        roundTrip(
            "sale/process-sale-response.json",
            com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
                .serializer(),
        ) {
            assertEquals(true, it.success)
            assertEquals("CUFE-123", it.cufe)
            assertEquals(false, it.sesionMesaCerrada)
        }
    }

    @Test
    fun `caja fixtures mantienen el contrato`() {
        roundTrip("caja/caja-apertura-request.json", AperturaRequest.serializer()) {
            assertEquals(50.0, it.montoApertura, 0.0)
        }
        roundTrip("caja/caja-cierre-request.json", CajaCierreSaveRequest.serializer()) { cierre ->
            assertEquals(172.0, cierre.montoTotal, 0.0)
            assertEquals(1, cierre.detalleFormaPago.size)
            assertEquals("cierre sin diferencias", cierre.observacionCierre)
        }
    }

    @Test
    fun `mesas fixtures mantienen el contrato`() {
        roundTrip("mesas/mesa-abrir-sesion-request.json", AbrirSesionRequest.serializer()) {
            assertEquals(2, it.cantidadPersonas)
        }
        roundTrip("mesas/mesa-crear-pedido-request.json", CrearPedidoMesaRequest.serializer()) {
            assertEquals(true, it.enviarInmediato)
            assertEquals("PRODUCTO", it.items.single().itemDescripcion)
        }
        roundTrip("mesas/mesa-crear-cuenta-request.json", CrearCuentaRequest.serializer()) {
            assertEquals(101, it.items.single().pedidoMesaId)
            assertEquals(false, it.incluirTodoPendiente)
        }
    }

    @Test
    fun `credit note y fiscal confirmation mantienen el contrato`() {
        roundTrip("creditnote/create-credit-note-request.json", CreateCreditNoteRequest.serializer()) {
            assertEquals("REINTEGRO", it.settlementType.name)
        }
        roundTrip(
            "creditnote/confirm-credit-note-fiscal-request.json",
            ConfirmCreditNoteFiscalRequest.serializer(),
        ) {
            assertEquals("NC-000001", it.codDevolucionFiscal)
        }
        roundTrip("fiscal/confirm-factura-fiscal-request.json", ConfirmFacturaFiscalRequest.serializer()) {
            assertEquals("SN-PRN-7", it.impresoraSerial)
        }
    }
}
