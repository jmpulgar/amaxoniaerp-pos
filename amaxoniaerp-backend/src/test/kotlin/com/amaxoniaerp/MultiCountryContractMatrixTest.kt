package com.amaxoniaerp

import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TASK-093 — Matriz de respuestas multi-país sobre los MISMOS fixtures de
 * `contracts/`: PA (CUFE/DGI), VE digital (The Factory HKA) y VE HKA-20.
 *
 * Los tres países comparten `ProcessSaleResponse`; el discriminador del país/
 * modalidad en el cable es la PRESENCIA/AUSENCIA de los campos fiscales
 * opcionales (`encodeDefaults=false`, `explicitNulls=false`):
 * - PA: cufe != null, numeroDocumentoFiscal == null.
 * - VE digital / VE HKA-20: numeroDocumentoFiscal != null, cufe == null.
 */
class MultiCountryContractMatrixTest {
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

    private fun <T> decode(
        relPath: String,
        serializer: KSerializer<T>,
    ): T = json.decodeFromString(serializer, Files.readString(contractsRoot().resolve(relPath)))

    private data class CountryRow(
        val country: String,
        val fixture: String,
    )

    @Test
    fun `matriz multi-pais discrimina por presencia de campos fiscales`() {
        val rows =
            listOf(
                CountryRow("PA", "sale/process-sale-response.json"),
                CountryRow("VE_DIGITAL", "sale/ve-digital-process-sale-response.json"),
                CountryRow("VE_HKA20", "sale/ve-hka20-process-sale-response.json"),
            )
        rows.forEach { row ->
            val response =
                decode(
                    row.fixture,
                    com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
                        .serializer(),
                )
            assertTrue(response.success, "$row.country: la respuesta debe ser exitosa")
            when (row.country) {
                "PA" -> {
                    assertNotNull(response.cufe, "$row.country: CUFE debe estar presente")
                    assertNull(response.numeroDocumentoFiscal, "$row.country: campo VE debe ser null")
                    assertNull(response.numeroControlThka, "$row.country: campo VE debe ser null")
                }
                else -> {
                    assertNotNull(response.numeroDocumentoFiscal, "${row.country}: documento fiscal VE debe venir")
                    assertTrue(
                        response.numeroDocumentoFiscal.isNotBlank(),
                        "${row.country}: documento fiscal VE no debe ser vacío",
                    )
                    assertNotNull(response.numeroControlThka, "${row.country}: control HKA debe venir")
                    assertNull(response.cufe, "${row.country}: campo PA debe ser null")
                    assertNull(response.qr, "${row.country}: campo PA debe ser null")
                }
            }
        }
    }

    @Test
    fun `useHka20 es el discriminador HKA-20 ausente por defecto y presente como true al aplicar`() {
        // Fixture canónico: sin "useHka20" en el cable -> null en el DTO nullable del
        // backend, y la clave no reaparece al re-encodar (encodeDefaults=false).
        val contado =
            decode("sale/process-sale-request.json", ProcessSaleRequest.serializer())
        assertNull(contado.useHka20, "el request canónico no declara HKA-20")
        val cableContado = json.parseToJsonElement(json.encodeToString(ProcessSaleRequest.serializer(), contado))
        assertTrue("useHka20" !in cableContado.jsonObject, "useHka20 ausente no debe viajar en el cable")

        // La MISMA venta con HKA-20 encendido: true != default -> la clave SÍ viaja.
        val cableHka = json.encodeToString(ProcessSaleRequest.serializer(), contado.copy(useHka20 = true))
        val objetoHka = json.parseToJsonElement(cableHka).jsonObject
        assertEquals(
            true,
            objetoHka.getValue("useHka20").jsonPrimitive.boolean,
            "useHka20=true debe sobrevivir el cable",
        )
    }
}
