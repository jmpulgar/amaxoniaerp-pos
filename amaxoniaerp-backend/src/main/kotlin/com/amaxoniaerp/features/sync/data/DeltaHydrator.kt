package com.amaxoniaerp.features.sync.data

import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.items.data.ItemsTableFactory
import com.amaxoniaerp.features.items.data.mapRowToProductSync
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.sync.domain.ClientTypeSyncDto
import com.amaxoniaerp.features.sync.domain.PromotionDetailSyncDto
import com.amaxoniaerp.features.sync.domain.PromotionSyncDto
import com.amaxoniaerp.features.sync.domain.SyncEntityType
import com.amaxoniaerp.features.sync.domain.SyncScope
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll

/**
 * Hidratación del delta: dado (entity_type, entity_id) resuelve el efecto
 * real para el dispositivo — UPSERT con la fila vigente si existe y está en
 * alcance, DELETE si no existe o quedó fuera de alcance (scope-exit, §18.3).
 */
class DeltaHydrator(
    private val reader: SyncCatalogReader,
) {
    fun hydrate(
        type: SyncEntityType,
        entityId: String,
        countryCode: String,
        scope: SyncScope,
    ): Hydrated =
        when (type) {
            SyncEntityType.PRODUCT -> hydrateProduct(entityId, countryCode, scope)
            SyncEntityType.CLIENT -> hydrateClient(entityId, scope)
            SyncEntityType.CLIENT_BRANCH ->
                hydrateByIntId(ClientSucursalTable.selectAll(), ClientSucursalTable.sucursalId, entityId) {
                    mapClientBranchSync(it)
                }
            SyncEntityType.CLIENT_TYPE -> hydrateFromList(entityId) { reader.loadClientTypes() }
            SyncEntityType.PROMOTION -> hydrateFromList(entityId) { reader.promotionPage().items }
            SyncEntityType.PROMOTION_DETAIL -> hydrateFromList(entityId) { reader.promotionDetailPage().items }
            SyncEntityType.PAYMENT_METHOD ->
                hydrateByIntId(CajaFormaPagoTable.selectAll(), CajaFormaPagoTable.idFormaPago, entityId) {
                    mapPaymentMethodSync(it)
                }
            SyncEntityType.CAJA_PAYMENT_METHOD -> hydrateComposite(entityId)
        }

    private fun hydrateProduct(
        entityId: String,
        countryCode: String,
        scope: SyncScope,
    ): Hydrated {
        val table = ItemsTableFactory.getTableForCountry(countryCode)
        val rows =
            table
                .selectAll()
                .andWhere { table.idItem eq (entityId.toIntOrNull() ?: -1) }
                .limit(1)
                .toList()
        val row = rows.firstOrNull() ?: return Hydrated.Delete
        val dto = mapRowToProductSync(row, countryCode)
        if (!scope.allProducts && dto.department !in scope.departmentIds) return Hydrated.Delete
        return Hydrated.Upsert(dto)
    }

    private fun hydrateClient(
        entityId: String,
        scope: SyncScope,
    ): Hydrated {
        val rows =
            ClientsTable
                .selectAll()
                .andWhere { ClientsTable.idCliente eq entityId }
                .limit(1)
                .toList()
        val row = rows.firstOrNull() ?: return Hydrated.Delete
        val dto = mapClientSync(row)
        if (!scope.allClients && dto.idSucursal !in scope.branchIds) return Hydrated.Delete
        return Hydrated.Upsert(dto)
    }

    private fun hydrateByIntId(
        base: Query,
        keyColumn: org.jetbrains.exposed.sql.Column<Int>,
        entityId: String,
        map: (org.jetbrains.exposed.sql.ResultRow) -> Any,
    ): Hydrated {
        val rows =
            base
                .andWhere { keyColumn eq (entityId.toIntOrNull() ?: -1) }
                .limit(1)
                .toList()
        val row = rows.firstOrNull() ?: return Hydrated.Delete
        return Hydrated.Upsert(map(row))
    }

    private fun hydrateFromList(
        entityId: String,
        load: () -> List<Any>,
    ): Hydrated {
        val dto =
            load().firstOrNull { dto ->
                when (dto) {
                    is ClientTypeSyncDto -> dto.id.toString() == entityId
                    is PromotionSyncDto -> dto.id == entityId
                    is PromotionDetailSyncDto -> dto.id == entityId
                    else -> false
                }
            } ?: return Hydrated.Delete
        return Hydrated.Upsert(dto)
    }

    private fun hydrateComposite(entityId: String): Hydrated {
        val parts = entityId.split(':')
        if (parts.size != 2) return Hydrated.Delete
        val rows =
            CajaFormaTable
                .selectAll()
                .andWhere {
                    (CajaFormaTable.idCaja eq parts[0]) and (CajaFormaTable.idFormaPago eq (parts[1].toIntOrNull() ?: -1))
                }.limit(1)
                .toList()
        val row = rows.firstOrNull() ?: return Hydrated.Delete
        return Hydrated.Upsert(mapCajaPaymentMethodSync(row))
    }

    sealed interface Hydrated {
        data class Upsert(
            val payload: Any,
        ) : Hydrated

        data object Delete : Hydrated
    }
}
