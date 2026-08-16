package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.core.database.dbQuery
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager

/**
 * Devuelve (id_item, cantidad_total) ordenados por cantidad descendente.
 * Solo líneas no anuladas; id_item no nulo.
 */
suspend fun getBestSellerItemQuantities(
    database: Database,
    limit: Int = 20,
): List<Pair<Int, Long>> =
    dbQuery(database) {
        val safeLimit = limit.coerceIn(1, 100)
        @Suppress("SqlSourceToSinkFlow") // safeLimit es Int acotado
        TransactionManager.current().exec(
            "SELECT id_item, SUM(_item_cantidad) AS total FROM factura_detalle WHERE (anulado = 0 OR anulado IS " +
                "NULL) " +
                "AND id_item IS NOT NULL GROUP BY id_item ORDER BY total DESC LIMIT $safeLimit",
        ) { result ->
            val list = mutableListOf<Pair<Int, Long>>()
            while (result.next()) {
                val id = result.getInt("id_item")
                val total = result.getBigDecimal("total")?.toLong() ?: 0L
                list.add(id to total)
            }
            list
        } ?: emptyList()
    }
