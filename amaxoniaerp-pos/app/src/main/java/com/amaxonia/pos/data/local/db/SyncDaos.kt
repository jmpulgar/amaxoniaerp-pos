package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncStateDao {
    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE tenantId = :tenantId AND scope = :scope LIMIT 1")
    suspend fun get(
        tenantId: String,
        scope: String,
    ): SyncStateEntity?

    @Query("DELETE FROM sync_state WHERE tenantId = :tenantId")
    suspend fun clearForTenant(tenantId: String)

    @Query("DELETE FROM sync_state")
    suspend fun clearAll()
}

@Dao
interface PaymentMethodDao {
    @Upsert
    suspend fun upsertAll(items: List<PaymentMethodEntity>)

    @Upsert
    suspend fun upsertCajaMappings(items: List<CajaPaymentMethodEntity>)

    @Query("DELETE FROM payment_methods")
    suspend fun clearPaymentMethods()

    @Query("DELETE FROM caja_payment_methods")
    suspend fun clearCajaMappings()

    @Query("SELECT * FROM payment_methods ORDER BY orden, descripcion")
    suspend fun getAll(): List<PaymentMethodEntity>

    @Query(
        "SELECT pm.* FROM payment_methods pm " +
            "INNER JOIN caja_payment_methods cm ON cm.idFormaPago = pm.idFormaPago " +
            "WHERE cm.idCaja = :cajaId AND (cm.activo IS NULL OR cm.activo = 1) " +
            "ORDER BY pm.orden, pm.descripcion",
    )
    suspend fun getByCaja(cajaId: String): List<PaymentMethodEntity>
}

@Dao
interface CajaSesionDao {
    @Upsert
    suspend fun upsert(sesion: CajaSesionEntity)

    @Query(
        "SELECT * FROM caja_sesion WHERE tenantId = :tenantId AND estado = 'ABIERTA' " +
            "ORDER BY openedAt DESC LIMIT 1",
    )
    suspend fun getAbierta(tenantId: String): CajaSesionEntity?

    @Query(
        "SELECT * FROM caja_sesion WHERE tenantId = :tenantId AND cajaId = :cajaId AND estado = 'ABIERTA' " +
            "ORDER BY openedAt DESC LIMIT 1",
    )
    suspend fun getAbiertaPorCaja(tenantId: String, cajaId: String): CajaSesionEntity?

    @Query(
        "UPDATE caja_sesion SET estado = 'CERRADA', closedAt = :closedAt " +
            "WHERE tenantId = :tenantId AND cajaId = :cajaId AND estado = 'ABIERTA'",
    )
    suspend fun markCerradaPorCaja(tenantId: String, cajaId: String, closedAt: Long)

    @Query("SELECT * FROM caja_sesion WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): CajaSesionEntity?

    @Query("DELETE FROM caja_sesion")
    suspend fun clearAll()
}
