package com.amaxoniaerp.features.promotions.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.promotions.domain.PromotionDetailResponse
import com.amaxoniaerp.features.promotions.domain.PromotionResponse
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp

class PromotionsRepository {
    suspend fun listPromotions(database: Database): List<PromotionResponse> =
        dbQuery(database) {
            val headers =
                TransactionManager.current().exec(
                    """
                    SELECT
                        id, id_item, codigo, inicio, fin, promocion, imagen, activo, descuento_global
                    FROM promocion
                    WHERE activo = 1
                    ORDER BY inicio DESC, id DESC
                    """.trimIndent(),
                ) { rs ->
                    buildList {
                        while (rs.next()) add(rs.toPromotionHeader())
                    }
                } ?: emptyList()

            if (headers.isEmpty()) return@dbQuery emptyList()

            val ids = headers.joinToString(",") { it.id.toIntOrNull()?.toString() ?: "0" }
            val detailsByPromotion =
                TransactionManager.current().exec(
                    """
                    SELECT
                        pd.id, pd.id_promocion, pd.id_item, pd.cantidad, pd.cantidad_total, pd.unidad_empaque,
                        pd.descuento, pd.descuento_monto, pd.id_tipo_precio, pd.precio, pd.impuesto,
                        pd.impuesto_porcentaje, pd.importe, pd.grupo,
                        COALESCE(i.descripcion1, '') AS item_descripcion,
                        COALESCE(i.cod_item, '') AS item_codigo
                    FROM promocion_detalle pd
                    LEFT JOIN item i ON i.id_item = pd.id_item
                    WHERE pd.id_promocion IN ($ids)
                    ORDER BY pd.id_promocion, pd.grupo, pd.id
                    """.trimIndent(),
                ) { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getString("id_promocion") to rs.toPromotionDetail())
                        }
                    }.groupBy({ it.first }, { it.second })
                } ?: emptyMap()

            headers.map { header -> header.copy(detalle = detailsByPromotion[header.id].orEmpty()) }
        }

    private fun ResultSet.toPromotionHeader(): PromotionResponse =
        PromotionResponse(
            id = getString("id"),
            codigo = getString("codigo") ?: "",
            inicio = getTimestampOrNull("inicio")?.toLocalDateTime()?.toString()?.replace("T", " "),
            fin = getTimestampOrNull("fin")?.toLocalDateTime()?.toString()?.replace("T", " "),
            promocion = getString("promocion") ?: "",
            imagen = getString("imagen") ?: "",
            activo = getInt("activo") == 1,
            descuentoGlobal = getBigDecimalOrZero("descuento_global").toDouble(),
            idItem = getString("id_item") ?: "",
        )

    private fun ResultSet.toPromotionDetail(): PromotionDetailResponse {
        val impuestoPorcentaje = getBigDecimalOrZero("impuesto_porcentaje").toDouble()
        return PromotionDetailResponse(
            idPromocionDetalle = getString("id"),
            idItem = getString("id_item") ?: "",
            idTipoPrecio = getString("id_tipo_precio") ?: "",
            cantidad = getBigDecimalOrZero("cantidad").toDouble(),
            cantidadTotal = getBigDecimalOrZero("cantidad_total").toDouble(),
            unidadEmpaque = getString("unidad_empaque") ?: "",
            descuento = getString("descuento")?.toDoubleOrNull() ?: 0.0,
            descuentoMonto = getBigDecimalOrZero("descuento_monto").toDouble(),
            precio = getBigDecimalOrZero("precio").toDouble(),
            impuesto = getBigDecimalOrZero("impuesto").toDouble(),
            impuestoPromocionDetalle = impuestoPorcentaje,
            impuestoPorcentaje = impuestoPorcentaje,
            importe = getBigDecimalOrZero("importe").toDouble(),
            grupo = getString("grupo") ?: "",
            itemDescripcion = runCatching { getString("item_descripcion") }.getOrNull()?.trim().orEmpty(),
            itemCodigo = runCatching { getString("item_codigo") }.getOrNull()?.trim().orEmpty(),
        )
    }

    private fun ResultSet.getTimestampOrNull(column: String): Timestamp? = runCatching { getTimestamp(column) }.getOrNull()

    private fun ResultSet.getBigDecimalOrZero(column: String): BigDecimal =
        runCatching { getBigDecimal(column) }.getOrNull() ?: BigDecimal.ZERO
}
