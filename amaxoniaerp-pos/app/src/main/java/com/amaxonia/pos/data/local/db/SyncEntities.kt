package com.amaxonia.pos.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Estado de sincronización por (empresa, ámbito). El cursor vive AQUÍ y se
 * persiste en la MISMA transacción Room que el lote de datos aplicado —
 * checkpoint kill-safe (PLAN_OFFLINE_SYNC_DESIGN.md §8).
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

/** Catálogo de formas de pago (ex `caja_forma_pago`), antes solo en DataStore. */
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

/** Mapeo caja ↔ forma de pago (ex `caja_forma`, PK compuesta). */
@Entity(tableName = "caja_payment_methods", primaryKeys = ["idCaja", "idFormaPago"])
data class CajaPaymentMethodEntity(
    val idCaja: String,
    val idFormaPago: Int,
    val activo: Int? = null,
)

/**
 * Sesión de caja persistente (PLAN §5.1-4): copia local de la secuencia
 * abierta para poder vender y reiniciar la app sin red. Fase 1 de caja
 * offline; la apertura/cierre offline completo es Fase 2 (Q12b).
 */
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
    /** CajaSecuencia serializada para restaurar la sesión exacta tras reinicio offline. */
    val secuenciaJson: String = "",
)
