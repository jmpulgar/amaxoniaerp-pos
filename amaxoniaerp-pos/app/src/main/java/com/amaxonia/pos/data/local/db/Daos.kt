package com.amaxonia.pos.data.local.db

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

    /** Purga de alcance (ADR-008): conserva solo clientes de las sucursales dadas. */
    @Query("DELETE FROM clients WHERE idSucursal IS NULL OR idSucursal NOT IN (:sucursalIds)")
    suspend fun deleteBySucursalesNotIn(sucursalIds: List<Int>)

    @Query("SELECT COUNT(*) FROM clients WHERE status = 1")
    suspend fun count(): Int

    @Query("SELECT * FROM clients ORDER BY name, lastName LIMIT :limit OFFSET :offset")
    suspend fun getPaged(
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

    @Query("SELECT * FROM client_sucursales WHERE clienteCodigo = :clienteCodigo ORDER BY nombreSucursal")
    suspend fun getByClientCode(clienteCodigo: String): List<ClientSucursalEntity>

    @Query("SELECT * FROM client_sucursales WHERE clienteCodigo IN (:clienteCodigos) ORDER BY nombreSucursal")
    suspend fun getByClientCodes(clienteCodigos: List<String>): List<ClientSucursalEntity>
}

@Dao
interface ProductDao {
    /**
     * D4: la superficie de venta solo expone ítems activos (`estatus = 'A'`;
     * valor a confirmar en staging). El registro inactivo permanece en la BD.
     * Nota: 'A' está literal en las queries; única fuente: PLAN §5.1-5.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductEntity>)

    @Upsert
    suspend fun upsertAll(items: List<ProductEntity>)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Purga de alcance (ADR-008): conserva solo productos de los departamentos dados. */
    @Query("DELETE FROM products WHERE department NOT IN (:departmentIds)")
    suspend fun deleteByDepartmentsNotIn(departmentIds: List<Int>)

    @Query("SELECT * FROM products WHERE id = :id AND estatus = 'A' LIMIT 1")
    suspend fun getById(id: String): ProductEntity?

    @Query(
        "SELECT * FROM products WHERE (barcode1 = :code OR barcode2 = :code OR barcode3 = :code) " +
            "AND estatus = 'A' LIMIT 1",
    )
    suspend fun getByBarcode(code: String): ProductEntity?

    @Query("SELECT * FROM products WHERE estatus = 'A' ORDER BY description LIMIT :limit OFFSET :offset")
    suspend fun getPaged(
        limit: Int,
        offset: Int,
    ): List<ProductEntity>

    @Query(
        "SELECT * FROM products WHERE department = :departmentId AND estatus = 'A' " +
            "ORDER BY description LIMIT :limit OFFSET :offset",
    )
    suspend fun getPagedByDepartment(
        departmentId: Int,
        limit: Int,
        offset: Int,
    ): List<ProductEntity>

    @Query(
        "SELECT * FROM products " +
            "WHERE estatus = 'A' AND (" +
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
    ): List<ProductEntity>

    @Query(
        "SELECT * FROM products " +
            "WHERE department = :departmentId AND estatus = 'A' AND (" +
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
    ): List<ProductEntity>

    /** Resync: borra TODO el catálogo de productos (nunca toca ventas). */
    @Query("DELETE FROM products")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM products WHERE estatus = 'A'")
    suspend fun count(): Int
}

@Dao
interface CountryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CountryEntity>)

    @Query("SELECT * FROM countries ORDER BY name")
    suspend fun getAll(): List<CountryEntity>
}

@Dao
interface AddressLevel1Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AddressLevel1Entity>)

    @Query("SELECT * FROM address_level1 WHERE countryCode = :countryCode ORDER BY code")
    suspend fun getByCountry(countryCode: String): List<AddressLevel1Entity>
}

@Dao
interface AddressLevel2Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AddressLevel2Entity>)

    @Query(
        "SELECT * FROM address_level2 " +
            "WHERE countryCode = :countryCode AND code LIKE :level1Code || '%' " +
            "ORDER BY code",
    )
    suspend fun getByLevel1(
        countryCode: String,
        level1Code: String,
    ): List<AddressLevel2Entity>
}

@Dao
interface AddressLevel3Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AddressLevel3Entity>)

    @Query(
        "SELECT * FROM address_level3 " +
            "WHERE countryCode = :countryCode AND code LIKE :level2Code || '%' " +
            "ORDER BY code",
    )
    suspend fun getByLevel2(
        countryCode: String,
        level2Code: String,
    ): List<AddressLevel3Entity>
}

@Dao
interface ClientTypeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClientTypeEntity>)

    @Upsert
    suspend fun upsertAll(items: List<ClientTypeEntity>)

    @Query("DELETE FROM client_types WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM client_types ORDER BY name")
    suspend fun getAll(): List<ClientTypeEntity>
}
