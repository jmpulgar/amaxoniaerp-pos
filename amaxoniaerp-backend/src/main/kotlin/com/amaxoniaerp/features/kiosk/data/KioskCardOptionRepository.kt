package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.kiosk.domain.KioskCardOptionDto
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

/**
 * Formas de pago con tarjeta que el kiosco puede ofrecer (VISA, MASTERCARD, débito...): filas
 * activas y visibles en POS de `caja_forma_pago` con siglas de tarjeta o código DGI de tarjeta
 * (`FormaPagoFact` 03 crédito / 04 débito). Lectura defensiva: ante cualquier error, lista vacía.
 */
class KioskCardOptionRepository {
    suspend fun list(database: Database): List<KioskCardOptionDto> =
        runCatching {
            dbQuery(database) {
                CajaFormaPagoTable
                    .selectAll()
                    .where { (CajaFormaPagoTable.activo eq 1) and (CajaFormaPagoTable.pos eq 1) }
                    .orderBy(CajaFormaPagoTable.orden to SortOrder.ASC, CajaFormaPagoTable.idFormaPago to SortOrder.ASC)
                    .mapNotNull { row ->
                        val siglas = row[CajaFormaPagoTable.siglas]?.trim()?.uppercase().orEmpty()
                        val dgiCode = row[CajaFormaPagoTable.formaPagoFact]?.trim().orEmpty()
                        if (siglas !in CARD_SIGLAS && dgiCode !in CARD_DGI_CODES) return@mapNotNull null
                        val id = row[CajaFormaPagoTable.idFormaPago]
                        KioskCardOptionDto(
                            id = id,
                            name = row[CajaFormaPagoTable.descripcion]?.trim()?.takeIf { it.isNotEmpty() } ?: siglas.ifEmpty { "TARJETA" },
                            siglas = siglas,
                            image = row[CajaFormaPagoTable.imagen].trim().takeIf { it.startsWith("data:image/") },
                        )
                    }
            }
        }.getOrDefault(emptyList())

    suspend fun find(
        database: Database,
        idFormaPago: Int,
    ): KioskCardOptionDto? = list(database).firstOrNull { it.id == idFormaPago }

    private companion object {
        val CARD_SIGLAS = setOf("TDC", "TDD", "AMEX", "TARJETA")
        val CARD_DGI_CODES = setOf("03", "04")
    }
}
