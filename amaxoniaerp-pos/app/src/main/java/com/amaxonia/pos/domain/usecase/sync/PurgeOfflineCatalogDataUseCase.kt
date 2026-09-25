package com.amaxonia.pos.domain.usecase.sync

import android.content.Context
import androidx.room.withTransaction
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.clearCatalogCache
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.sync.OfflineSyncSettingsStore
import com.amaxonia.pos.data.sync.SyncScheduler

/**
 * Purga todos los datos descargados del modo offline al cerrar sesión:
 * 1. Cancela las tareas de sincronización de catálogos en WorkManager.
 * 2. Limpia atómicamente todas las tablas de catálogo en Room Database (productos, clientes,
 *    sucursales, promociones, formas de pago, países, niveles de dirección, tipos de cliente,
 *    sesiones de caja, borradores y estados de sync).
 *    NOTA: Se conservan `pending_invoices` y `transaction_log` para no perder ventas offline pendientes.
 * 3. Limpia los cachés de DataStore (productos, clientes, flags de sync completado por empresa y alcance offline).
 */
class PurgeOfflineCatalogDataUseCase(
    private val database: AppDatabase,
    private val localStore: LocalStore,
    private val offlineSyncSettingsStore: OfflineSyncSettingsStore,
    private val appContext: Context,
) {
    suspend operator fun invoke() {
        // 1. Cancelar tareas en segundo plano de sincronización de catálogo
        SyncScheduler.cancelCatalogSync(appContext)

        // 2. Limpiar tablas de catálogo en Room dentro de una transacción única
        database.withTransaction {
            database.productDao().clearAll()
            database.clientDao().clearAll()
            database.clientSucursalDao().clearAll()
            database.promocionDao().clearPromociones()
            database.promocionDao().clearDetalles()
            database.paymentMethodDao().clearPaymentMethods()
            database.paymentMethodDao().clearCajaMappings()
            database.countryDao().clearAll()
            database.addressLevel1Dao().clearAll()
            database.addressLevel2Dao().clearAll()
            database.addressLevel3Dao().clearAll()
            database.clientTypeDao().clearAll()
            database.syncStateDao().clearAll()
            database.cajaSesionDao().clearAll()
            database.draftInvoiceDao().deleteAll()
        }

        // 3. Limpiar cachés y flags en DataStore
        localStore.clearCatalogCache()
        offlineSyncSettingsStore.reset()
    }
}
