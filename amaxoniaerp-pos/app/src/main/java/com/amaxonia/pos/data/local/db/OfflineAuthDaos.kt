package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineUserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(users: List<OfflineUserEntity>)

    @Query(
        "SELECT * FROM offline_users WHERE LOWER(username) = LOWER(:username) " +
            "AND countryCode = :countryCode LIMIT 1",
    )
    suspend fun findByUsernameAndCountry(username: String, countryCode: String): OfflineUserEntity?

    @Query("SELECT COUNT(*) FROM offline_users WHERE countryCode = :countryCode AND status = 'A'")
    suspend fun countActiveUsers(countryCode: String): Int

    @Query("DELETE FROM offline_users WHERE countryCode = :countryCode")
    suspend fun clearByCountry(countryCode: String)

    @Query("DELETE FROM offline_users")
    suspend fun clearAll()
}

@Dao
interface OfflineCompanyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(companies: List<OfflineCompanyEntity>)

    @Query("SELECT * FROM offline_companies WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<OfflineCompanyEntity>

    @Query("SELECT * FROM offline_companies WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): OfflineCompanyEntity?

    @Query("DELETE FROM offline_companies WHERE countryCode = :countryCode")
    suspend fun clearByCountry(countryCode: String)

    @Query("DELETE FROM offline_companies")
    suspend fun clearAll()
}
