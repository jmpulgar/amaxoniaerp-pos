package com.amaxonia.pos.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.amaxonia.pos.domain.model.PriceLevel

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val code: String,
    val identification: String,
    val dv: String,
    val name: String,
    val lastName: String,
    val address: String,
    val phone: String,
    val email: String,
    val status: Boolean,
    val clientTypeId: Int,       // Correcto: Int
    val taxpayerTypeId: Int,     // CORREGIDO: Ahora es Int (antes String taxpayerType)
    val countryId: Int,
    val addressLevel1: String,
    val addressLevel2: String,
    val addressLevel3: String
)

// ... (El resto de las entidades ProductEntity, CountryEntity, etc. déjalas igual) ...
// Copia aquí abajo el resto de las entidades que ya tenías (ProductEntity, etc) sin cambios.
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val code: String,
    val description: String,
    val reference: String,
    val barcode1: String,
    val barcode2: String,
    val barcode3: String,
    val department: Int,
    val isExempt: Boolean,
    val taxRate: Double,
    val costActual: Double,
    val prices: List<PriceLevel>
)

@Entity(tableName = "countries")
data class CountryEntity(
    @PrimaryKey val id: Int,
    val iso: String,
    val name: String
)

@Entity(
    tableName = "address_level1",
    primaryKeys = ["countryCode", "code"]
)
data class AddressLevel1Entity(
    val countryCode: String,
    val code: String,
    val name: String
)

@Entity(
    tableName = "address_level2",
    primaryKeys = ["countryCode", "code"]
)
data class AddressLevel2Entity(
    val countryCode: String,
    val code: String,
    val name: String
)

@Entity(
    tableName = "address_level3",
    primaryKeys = ["countryCode", "code"]
)
data class AddressLevel3Entity(
    val countryCode: String,
    val code: String,
    val name: String
)

@Entity(tableName = "client_types")
data class ClientTypeEntity(
    @PrimaryKey val id: Int,
    val name: String
)
