package com.amaxonia.pos.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Usuario descargado de la BD de configuración para autenticación offline.
 */
@Entity(
    tableName = "offline_users",
    indices = [Index(value = ["username", "countryCode"], unique = true)],
)
data class OfflineUserEntity(
    @PrimaryKey val id: Int,
    val username: String,
    val passwordHash: String,
    val role: String,
    val levelId: Int,
    val companyCodes: List<Int>,
    val countryCode: String,
    val status: String = "A",
    val updatedAt: Long = 0,
)

/**
 * Empresa descargada de la BD de configuración para selección offline.
 */
@Entity(tableName = "offline_companies")
data class OfflineCompanyEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val adminDb: String,
    val accountingDb: String = "",
    val payrollDb: String = "",
    val rif: String = "",
    val countryCode: String = "",
    val updatedAt: Long = 0,
)
