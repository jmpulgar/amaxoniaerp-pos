package com.amaxonia.pos.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
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
    val clientTypeId: Int, // Correcto: Int
    val taxpayerTypeId: Int, // CORREGIDO: Ahora es Int (antes String taxpayerType)
    val countryId: Int,
    val addressLevel1: String,
    val addressLevel2: String,
    val addressLevel3: String,
    @ColumnInfo(defaultValue = "0") val permiteCredito: Boolean = false,
    @ColumnInfo(defaultValue = "0") val diasCredito: Int = 0,
    @ColumnInfo(defaultValue = "2") val codTipoPrecio: Int = 2,
    /** Sucursal de Amaxonia propietaria del cliente (filtro de alcance, ADR-008). */
    val idSucursal: Int? = null,
)

@Entity(tableName = "client_sucursales")
data class ClientSucursalEntity(
    @PrimaryKey val sucursalId: Int,
    val clienteCodigo: String,
    val nombreSucursal: String,
    val nombreContacto: String? = null,
    val telefonoContacto: String? = null,
    val correoContacto: String? = null,
    val direccion: String? = null,
    val observaciones: String? = null,
)

// ... (El resto de las entidades ProductEntity, CountryEntity, etc. déjalas igual) ...
// Copia aquí abajo el resto de las entidades que ya tenías (ProductEntity, etc) sin cambios.
@Entity(
    tableName = "products",
    indices = [
        Index("barcode1"),
        Index("barcode2"),
        Index("barcode3"),
        Index("department"),
        Index("description"),
    ],
)
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
    val unitPackage: String = "",
    val bulkQuantity: Double = 1.0,
    val portionUnit: String? = null,
    val unitOrPackage: String = "UNIDAD",
    /**
     * Estado del ítem en el ERP (D4): solo los activos se venden; el registro
     * nunca se borra físicamente para proteger la integridad de ventas previas.
     * Valor de "activo" a confirmar en staging (default 'A').
     */
    @ColumnInfo(defaultValue = "A") val estatus: String = "A",
    @ColumnInfo(defaultValue = "0") val isService: Boolean = false,
    val prices: List<PriceLevel>,
)

@Entity(tableName = "countries")
data class CountryEntity(
    @PrimaryKey val id: Int,
    val iso: String,
    val name: String,
)

@Entity(
    tableName = "address_level1",
    primaryKeys = ["countryCode", "code"],
)
data class AddressLevel1Entity(
    val countryCode: String,
    val code: String,
    val name: String,
)

@Entity(
    tableName = "address_level2",
    primaryKeys = ["countryCode", "code"],
)
data class AddressLevel2Entity(
    val countryCode: String,
    val code: String,
    val name: String,
)

@Entity(
    tableName = "address_level3",
    primaryKeys = ["countryCode", "code"],
)
data class AddressLevel3Entity(
    val countryCode: String,
    val code: String,
    val name: String,
)

@Entity(tableName = "client_types")
data class ClientTypeEntity(
    @PrimaryKey val id: Int,
    val name: String,
)
