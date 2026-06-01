package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction

@Entity(tableName = "promociones")
data class PromocionEntity(
    @PrimaryKey val id: String,
    val codigo: String,
    val inicio: String?,
    val fin: String?,
    val nombre: String,
    val imagen: String,
    val descuentoGlobal: Double,
    val idItem: String,
    val activo: Boolean
)

@Entity(tableName = "promocion_detalles")
data class PromocionDetalleEntity(
    @PrimaryKey val id: String,
    val promocionId: String,
    val idItem: String,
    val idTipoPrecio: String,
    val cantidad: Double,
    val cantidadTotal: Double,
    val unidadEmpaque: String,
    val descuento: Double,
    val descuentoMonto: Double,
    val precio: Double,
    val impuesto: Double,
    val impuestoPorcentaje: Double,
    val importe: Double,
    val grupo: String
)

data class PromocionCompleta(
    @Embedded val promocion: PromocionEntity,
    @Relation(parentColumn = "id", entityColumn = "promocionId")
    val detalles: List<PromocionDetalleEntity>
)

@Dao
interface PromocionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromociones(items: List<PromocionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetalles(items: List<PromocionDetalleEntity>)

    @Query("DELETE FROM promociones")
    suspend fun clearPromociones()

    @Query("DELETE FROM promocion_detalles")
    suspend fun clearDetalles()

    @Transaction
    @Query("SELECT * FROM promociones WHERE activo = 1 ORDER BY nombre")
    suspend fun getAllActive(): List<PromocionCompleta>

    @Transaction
    @Query("SELECT * FROM promociones WHERE activo = 1 AND idItem = :productId ORDER BY nombre")
    suspend fun getActiveByParentProduct(productId: String): List<PromocionCompleta>

    @Transaction
    @Query("SELECT * FROM promociones WHERE id = :promotionId LIMIT 1")
    suspend fun getById(promotionId: String): PromocionCompleta?
}
