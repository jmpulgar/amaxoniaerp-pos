package com.amaxonia.erp.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Estado de sincronización por (empresa, ámbito). El cursor vive AQUÍ y se
 * persiste en la MISMA transacción Room que el lote de datos aplicado — checkpoint kill-safe.
 */
@Entity(tableName = "sync_state", primaryKeys = ["tenantId", "scope"])
data class SyncStateEntity(
    val tenantId: String,
    /** "GLOBAL" o el nombre de la entidad (PRODUCT, CLIENT, …). */
    val scope: String,
    val cursor: Long = 0,
    val snapshotId: Long = 0,
    val afterId: String? = null,
    val status: String = "IDLE",
    val updatedAt: Long = 0,
)

/** Catálogo de formas de pago persistido en Room para operar offline. */
@Entity(tableName = "payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey val idFormaPago: Int,
    val siglas: String? = null,
    val codigo: Int? = null,
    val descripcion: String? = null,
    val idCajaTpConcepto: Int? = null,
    val cuentaContable: String? = null,
    val idCajaTpRegistro: Int? = null,
    val formaPagoFact: String? = null,
    val activo: Int = 1,
    val pos: Int = 1,
    val imagen: String = "",
    val grupo: Int = 1,
    val orden: Int = 1,
    val idBancoCuenta: Int = 1,
    val idBancoOperacion: Int = 1,
    val tipoMoneda: String? = null,
)

/** Mapeo caja ↔ forma de pago. */
@Entity(tableName = "caja_payment_methods", primaryKeys = ["idCaja", "idFormaPago"])
data class CajaPaymentMethodEntity(
    val idCaja: String,
    val idFormaPago: Int,
    val activo: Int? = null,
)

/** Sesión local de caja persistente para vender y reiniciar sin red. */
@Entity(tableName = "caja_sesion")
data class CajaSesionEntity(
    @PrimaryKey val localId: String,
    val cajaId: String,
    val serverSecuenciaId: String? = null,
    val estado: String = "ABIERTA",
    val openedAt: Long = 0,
    val closedAt: Long? = null,
    val userId: String = "",
    val tenantId: String = "",
    val secuenciaJson: String = "",
)
