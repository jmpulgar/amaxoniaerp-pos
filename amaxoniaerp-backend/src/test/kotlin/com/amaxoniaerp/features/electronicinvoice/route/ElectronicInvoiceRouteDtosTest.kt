package com.amaxoniaerp.features.electronicinvoice.route

import com.amaxoniaerp.features.electronicinvoice.domain.IncidenciaFiscal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato JSON de las respuestas del endpoint /enviar. Las claves son las
 * que parsea el POS (ElectronicInvoiceResultDto + extraerMensajeErrorFe);
 * la respuesta NUNCA puede ser un Map<String, Any> mixto (el runtime de
 * kotlinx no soporta colecciones heterogéneas -> 500 post-éxito).
 */
class ElectronicInvoiceRouteDtosTest {
    private val json = Json

    @Test
    fun `respuesta de exito serializa las claves del contrato POS`() {
        val body =
            json.encodeToString(
                ElectronicInvoiceResponse.serializer(),
                ElectronicInvoiceResponse(
                    success = true,
                    cufe = "CUFE-1",
                    qr = "QR-DATA",
                    fechaRecepcionDGI = "2026-09-03T15:08:36-05:00",
                    nroProtocoloAutorizacion = "NPA-9",
                ),
            )
        val obj = Json.parseToJsonElement(body).jsonObject

        assertEquals(true, obj["success"]?.jsonPrimitive?.boolean)
        assertEquals("CUFE-1", obj["cufe"]?.jsonPrimitive?.contentOrNull)
        assertEquals("QR-DATA", obj["qr"]?.jsonPrimitive?.contentOrNull)
        assertEquals("NPA-9", obj["nroProtocoloAutorizacion"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `respuesta de ya emitida incluye cufe numero fiscal y bandera`() {
        val body =
            json.encodeToString(
                ElectronicInvoiceResponse.serializer(),
                ElectronicInvoiceResponse(
                    success = true,
                    message = "La factura ya posee numeración fiscal (3331)",
                    alreadyIssued = true,
                    numeroDocumentoFiscal = "3331",
                    cufe = "CUFE-PREVIO",
                ),
            )
        val obj = Json.parseToJsonElement(body).jsonObject

        assertEquals(true, obj["alreadyIssued"]?.jsonPrimitive?.boolean)
        assertEquals("3331", obj["numeroDocumentoFiscal"]?.jsonPrimitive?.contentOrNull)
        assertEquals("CUFE-PREVIO", obj["cufe"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `respuesta de fallo serializa codigo mensaje reintentable e incidencias fiscales`() {
        val body =
            json.encodeToString(
                ElectronicInvoiceFailureResponse.serializer(),
                ElectronicInvoiceFailureResponse(
                    success = false,
                    codigo = "203",
                    mensaje = "2007-Item 1: sin CPBS",
                    reintentable = false,
                    incidenciasFiscales = listOf(IncidenciaFiscal("2007", "Item 1: sin CPBS")),
                ),
            )
        val obj = Json.parseToJsonElement(body).jsonObject

        assertEquals(false, obj["success"]?.jsonPrimitive?.boolean)
        assertEquals("203", obj["codigo"]?.jsonPrimitive?.contentOrNull)
        assertEquals(false, obj["reintentable"]?.jsonPrimitive?.boolean)
        val primeraIncidencia = obj["incidenciasFiscales"]?.jsonArray?.single()?.jsonObject
        assertEquals("2007", primeraIncidencia?.get("codigo")?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `respuesta de fallo sin incidencias emite la lista vacia`() {
        val body =
            json.encodeToString(
                ElectronicInvoiceFailureResponse.serializer(),
                ElectronicInvoiceFailureResponse(
                    success = false,
                    codigo = "SEND_ERROR",
                    mensaje = "Error de comunicación con el PAC",
                    reintentable = true,
                    incidenciasFiscales = emptyList(),
                ),
            )
        val obj = Json.parseToJsonElement(body).jsonObject

        assertTrue(obj["incidenciasFiscales"]?.jsonArray?.isEmpty() == true)
    }

    @Test
    fun `respuesta incierta mantiene las claves de conciliacion`() {
        val body =
            json.encodeToString(
                ElectronicInvoiceUncertainResponse.serializer(),
                ElectronicInvoiceUncertainResponse(
                    success = false,
                    codigo = "SEND_ERROR",
                    mensaje = "Timeout",
                    incierta = true,
                    transaccionId = "TX-1",
                    action = "Requiere conciliación manual",
                ),
            )
        val obj = Json.parseToJsonElement(body).jsonObject

        assertEquals(false, obj["success"]?.jsonPrimitive?.boolean)
        assertEquals(true, obj["incierta"]?.jsonPrimitive?.boolean)
        assertEquals("TX-1", obj["transaccionId"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Requiere conciliación manual", obj["action"]?.jsonPrimitive?.contentOrNull)
    }
}
