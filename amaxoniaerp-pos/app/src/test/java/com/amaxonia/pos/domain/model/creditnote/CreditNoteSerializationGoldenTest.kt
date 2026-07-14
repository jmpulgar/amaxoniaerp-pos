package com.amaxonia.pos.domain.model.creditnote

import com.amaxonia.pos.data.local.AppJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class CreditNoteSerializationGoldenTest {
    @Test
    fun createRequestMatchesBackendContractGolden() {
        val request =
            CreateCreditNoteRequestDto(
                idFactura = "invoice-1",
                fecha = "2026-07-12",
                periodo = "2026-07",
                observacion = "DEVOLUCION",
                detalle = listOf(CreateCreditNoteLineInputDto("line-1", 1.5)),
                anular = false,
                devolverStock = true,
                idCajaSecuencia = "cash-sequence-1",
                settlementType = CreditNoteSettlementTypeDto.REINTEGRO,
                idFormaPagoReintegro = 2,
                devolucionElectronica = true,
                numeroFiscalElectronico = "FISCAL-001",
            )
        val actual = AppJson.parseToJsonElement(AppJson.encodeToString(request))
        val expected = AppJson.parseToJsonElement(readGolden("create-credit-note-request.json"))

        assertEquals(expected, actual)
        assertEquals(request, AppJson.decodeFromString(CreateCreditNoteRequestDto.serializer(), actual.toString()))
    }

    private fun readGolden(name: String): String {
        val uri = checkNotNull(javaClass.classLoader?.getResource("golden/$name")).toURI()
        return String(Files.readAllBytes(Paths.get(uri)), Charsets.UTF_8)
    }
}
