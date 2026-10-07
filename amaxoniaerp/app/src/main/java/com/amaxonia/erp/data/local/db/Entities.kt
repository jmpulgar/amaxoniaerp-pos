package com.amaxonia.erp.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amaxonia.erp.domain.model.PriceLevel

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
    val barcode2: String = "",
    val barcode3: String = "",
    val department: Int,
    val isExempt: Boolean,
    val taxRate: Double,
    val costActual: Double,
    val unitPackage: String,
    val bulkQuantity: Double = 1.0,
    val portionUnit: String? = null,
    val unitOrPackage: String = "UNIDAD",
    val estatus: String = "A",
    val isService: Boolean = false,
    val prices: List<PriceLevel>,
)

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val code: String,
    val identification: String,
    val dv: String = "0",
    val name: String,
    val lastName: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val status: Boolean = true,
    val clientTypeId: Int = 1,
    val taxpayerTypeId: Int = 1,
    val countryId: Int = 1,
    val addressLevel1: String = "",
    val addressLevel2: String = "",
    val addressLevel3: String = "",
    val permiteCredito: Boolean = false,
    val diasCredito: Int = 0,
    val codTipoPrecio: Int = 2,
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

@Entity(tableName = "client_types")
data class ClientTypeEntity(
    @PrimaryKey val id: Int,
    val name: String,
)

@Entity(tableName = "promociones")
data class PromocionEntity(
    @PrimaryKey val id: String,
    val codigo: String,
    val inicio: String? = null,
    val fin: String? = null,
    val nombre: String,
    val imagen: String,
    val descuentoGlobal: Double,
    val idItem: String,
    val activo: Boolean,
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
    val grupo: String,
)

@Entity(tableName = "departments")
data class DepartmentEntity(
    @PrimaryKey val id: Int,
    val name: String,
)
