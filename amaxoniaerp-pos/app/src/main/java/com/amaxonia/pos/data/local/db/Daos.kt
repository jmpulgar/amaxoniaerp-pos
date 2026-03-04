package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClientEntity>)

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ClientEntity?

    @Query("SELECT * FROM clients ORDER BY name, lastName LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<ClientEntity>

    @Query(
        "SELECT * FROM clients " +
            "WHERE name LIKE :query COLLATE NOCASE " +
            "OR lastName LIKE :query COLLATE NOCASE " +
            "OR identification LIKE :query COLLATE NOCASE " +
            "OR phone LIKE :query COLLATE NOCASE " +
            "OR email LIKE :query COLLATE NOCASE " +
            "ORDER BY name, lastName LIMIT :limit OFFSET :offset"
    )
    suspend fun searchPaged(query: String, limit: Int, offset: Int): List<ClientEntity>
}

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductEntity>)

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT * FROM products ORDER BY description LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<ProductEntity>

    @Query(
        "SELECT * FROM products " +
            "WHERE code LIKE :query COLLATE NOCASE " +
            "OR description LIKE :query COLLATE NOCASE " +
            "OR reference LIKE :query COLLATE NOCASE " +
            "OR barcode1 LIKE :query COLLATE NOCASE " +
            "ORDER BY description LIMIT :limit OFFSET :offset"
    )
    suspend fun searchPaged(query: String, limit: Int, offset: Int): List<ProductEntity>
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
            "ORDER BY code"
    )
    suspend fun getByLevel1(countryCode: String, level1Code: String): List<AddressLevel2Entity>
}

@Dao
interface AddressLevel3Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AddressLevel3Entity>)

    @Query(
        "SELECT * FROM address_level3 " +
            "WHERE countryCode = :countryCode AND code LIKE :level2Code || '%' " +
            "ORDER BY code"
    )
    suspend fun getByLevel2(countryCode: String, level2Code: String): List<AddressLevel3Entity>
}

@Dao
interface ClientTypeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClientTypeEntity>)

    @Query("SELECT * FROM client_types ORDER BY name")
    suspend fun getAll(): List<ClientTypeEntity>
}
