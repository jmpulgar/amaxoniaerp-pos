package com.amaxonia.erp.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClientEntity>)

    @Upsert
    suspend fun upsertAll(items: List<ClientEntity>)

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ClientEntity?

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Purga de alcance: conserva solo clientes de las sucursales dadas. */
    @Query("DELETE FROM clients WHERE idSucursal IS NULL OR idSucursal NOT IN (:sucursalIds)")
    suspend fun deleteBySucursalesNotIn(sucursalIds: List<Int>)

    @Query("DELETE FROM clients")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM clients WHERE status = 1")
    suspend fun count(): Int

    @Query("SELECT * FROM clients ORDER BY name, lastName LIMIT :limit OFFSET :offset")
    suspend fun getPaged(
        limit: Int,
        offset: Int,
    ): List<ClientEntity>

    @Query(
        "SELECT * FROM clients WHERE idSucursal IN (:sucursalIds) " +
            "ORDER BY name, lastName LIMIT :limit OFFSET :offset",
    )
    suspend fun getPagedBySucursales(
        sucursalIds: List<Int>,
        limit: Int,
        offset: Int,
    ): List<ClientEntity>

    @Query(
        "SELECT * FROM clients " +
            "WHERE name LIKE :query COLLATE NOCASE " +
            "OR lastName LIKE :query COLLATE NOCASE " +
            "OR identification LIKE :query COLLATE NOCASE " +
            "OR phone LIKE :query COLLATE NOCASE " +
            "OR email LIKE :query COLLATE NOCASE " +
            "ORDER BY name, lastName LIMIT :limit OFFSET :offset",
    )
    suspend fun searchPaged(
        query: String,
        limit: Int,
        offset: Int,
    ): List<ClientEntity>

    @Query(
        "SELECT * FROM clients " +
            "WHERE idSucursal IN (:sucursalIds) AND (" +
            "name LIKE :query COLLATE NOCASE " +
            "OR lastName LIKE :query COLLATE NOCASE " +
            "OR identification LIKE :query COLLATE NOCASE " +
            "OR phone LIKE :query COLLATE NOCASE " +
            "OR email LIKE :query COLLATE NOCASE) " +
            "ORDER BY name, lastName LIMIT :limit OFFSET :offset",
    )
    suspend fun searchPagedBySucursales(
        query: String,
        sucursalIds: List<Int>,
        limit: Int,
        offset: Int,
    ): List<ClientEntity>

    @Query("SELECT DISTINCT idSucursal FROM clients WHERE idSucursal IS NOT NULL")
    suspend fun getDistinctSucursalIds(): List<Int>
}

@Dao
interface ClientSucursalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClientSucursalEntity>)

    @Upsert
    suspend fun upsertAll(items: List<ClientSucursalEntity>)

    @Query("DELETE FROM client_sucursales WHERE sucursalId = :sucursalId")
    suspend fun deleteById(sucursalId: Int)

    @Query("DELETE FROM client_sucursales WHERE clienteCodigo = :clienteCodigo")
    suspend fun deleteByClientCode(clienteCodigo: String)

    @Query("DELETE FROM client_sucursales")
    suspend fun clearAll()

    @Query("SELECT * FROM client_sucursales WHERE clienteCodigo = :clienteCodigo ORDER BY nombreSucursal")
    suspend fun getByClientCode(clienteCodigo: String): List<ClientSucursalEntity>
}

@Dao
interface ClientTypeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClientTypeEntity>)

    @Upsert
    suspend fun upsertAll(items: List<ClientTypeEntity>)

    @Query("DELETE FROM client_types WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM client_types")
    suspend fun clearAll()
}

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductEntity>)

    @Upsert
    suspend fun upsertAll(items: List<ProductEntity>)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Purga de alcance: conserva solo productos de los departamentos dados. */
    @Query("DELETE FROM products WHERE department NOT IN (:departmentIds)")
    suspend fun deleteByDepartmentsNotIn(departmentIds: List<Int>)

    @Query("DELETE FROM products")
    suspend fun clearAll()

    @Query("SELECT * FROM products WHERE id = :id AND estatus = 'A' LIMIT 1")
    suspend fun getById(id: String): ProductEntity?

    @Query(
        "SELECT * FROM products WHERE (barcode1 = :code OR barcode2 = :code OR barcode3 = :code) " +
            "AND estatus = 'A' LIMIT 1",
    )
    suspend fun getByBarcode(code: String): ProductEntity?

    @Query(
        "SELECT * FROM products WHERE estatus = 'A' " +
            "AND (:isService IS NULL OR isService = :isService) " +
            "ORDER BY description LIMIT :limit OFFSET :offset",
    )
    suspend fun getPaged(
        limit: Int,
        offset: Int,
        isService: Int? = null,
    ): List<ProductEntity>

    @Query(
        "SELECT * FROM products WHERE department = :departmentId AND estatus = 'A' " +
            "AND (:isService IS NULL OR isService = :isService) " +
            "ORDER BY description LIMIT :limit OFFSET :offset",
    )
    suspend fun getPagedByDepartment(
        departmentId: Int,
        limit: Int,
        offset: Int,
        isService: Int? = null,
    ): List<ProductEntity>

    @Query(
        "SELECT * FROM products WHERE department IN (:departmentIds) AND estatus = 'A' " +
            "AND (:isService IS NULL OR isService = :isService) " +
            "ORDER BY description LIMIT :limit OFFSET :offset",
    )
    suspend fun getPagedByDepartments(
        departmentIds: List<Int>,
        limit: Int,
        offset: Int,
        isService: Int? = null,
    ): List<ProductEntity>

    @Query(
        "SELECT * FROM products " +
            "WHERE estatus = 'A' AND (:isService IS NULL OR isService = :isService) AND (" +
            "code LIKE :query COLLATE NOCASE " +
            "OR description LIKE :query COLLATE NOCASE " +
            "OR reference LIKE :query COLLATE NOCASE " +
            "OR barcode1 LIKE :query COLLATE NOCASE " +
            "OR barcode2 LIKE :query COLLATE NOCASE " +
            "OR barcode3 LIKE :query COLLATE NOCASE) " +
            "ORDER BY description LIMIT :limit OFFSET :offset",
    )
    suspend fun searchPaged(
        query: String,
        limit: Int,
        offset: Int,
        isService: Int? = null,
    ): List<ProductEntity>

    @Query(
        "SELECT * FROM products " +
            "WHERE department = :departmentId AND estatus = 'A' " +
            "AND (:isService IS NULL OR isService = :isService) AND (" +
            "code LIKE :query COLLATE NOCASE " +
            "OR description LIKE :query COLLATE NOCASE " +
            "OR reference LIKE :query COLLATE NOCASE " +
            "OR barcode1 LIKE :query COLLATE NOCASE " +
            "OR barcode2 LIKE :query COLLATE NOCASE " +
            "OR barcode3 LIKE :query COLLATE NOCASE) " +
            "ORDER BY description LIMIT :limit OFFSET :offset",
    )
    suspend fun searchPagedByDepartment(
        query: String,
        departmentId: Int,
        limit: Int,
        offset: Int,
        isService: Int? = null,
    ): List<ProductEntity>

    @Query(
        "SELECT * FROM products " +
            "WHERE department IN (:departmentIds) AND estatus = 'A' " +
            "AND (:isService IS NULL OR isService = :isService) AND (" +
            "code LIKE :query COLLATE NOCASE " +
            "OR description LIKE :query COLLATE NOCASE " +
            "OR reference LIKE :query COLLATE NOCASE " +
            "OR barcode1 LIKE :query COLLATE NOCASE " +
            "OR barcode2 LIKE :query COLLATE NOCASE " +
            "OR barcode3 LIKE :query COLLATE NOCASE) " +
            "ORDER BY description LIMIT :limit OFFSET :offset",
    )
    suspend fun searchPagedByDepartments(
        query: String,
        departmentIds: List<Int>,
        limit: Int,
        offset: Int,
        isService: Int? = null,
    ): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM products WHERE estatus = 'A'")
    suspend fun count(): Int

    @Query("SELECT DISTINCT department FROM products WHERE department IS NOT NULL")
    suspend fun getDistinctDepartmentIds(): List<Int>
}

@Dao
interface PromocionDao {
    @Upsert
    suspend fun upsertPromociones(items: List<PromocionEntity>)

    @Upsert
    suspend fun upsertDetalles(items: List<PromocionDetalleEntity>)

    @Query("DELETE FROM promociones WHERE id = :id")
    suspend fun deletePromocionById(id: String)

    @Query("DELETE FROM promocion_detalles WHERE id = :id")
    suspend fun deleteDetalleById(id: String)

    @Query("DELETE FROM promociones")
    suspend fun clearPromociones()

    @Query("DELETE FROM promocion_detalles")
    suspend fun clearDetalles()
}

@Dao
interface DepartmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DepartmentEntity>)

    @Query("SELECT * FROM departments ORDER BY name")
    suspend fun getAll(): List<DepartmentEntity>

    @Query("DELETE FROM departments")
    suspend fun clearAll()
}
