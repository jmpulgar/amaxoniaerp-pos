package com.amaxonia.pos.domain.usecase.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.clearCatalogCache
import com.amaxonia.pos.data.local.db.AddressLevel1Entity
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.CajaSesionEntity
import com.amaxonia.pos.data.local.db.ClientEntity
import com.amaxonia.pos.data.local.db.ClientSucursalEntity
import com.amaxonia.pos.data.local.db.CountryEntity
import com.amaxonia.pos.data.local.db.DraftInvoiceEntity
import com.amaxonia.pos.data.local.db.PaymentMethodEntity
import com.amaxonia.pos.data.local.db.PendingInvoiceEntity
import com.amaxonia.pos.data.local.db.ProductEntity
import com.amaxonia.pos.data.local.db.PromocionEntity
import com.amaxonia.pos.data.local.db.SyncStateEntity
import com.amaxonia.pos.data.local.db.TransactionLogEntity
import com.amaxonia.pos.data.local.isInitialSyncCompleted
import com.amaxonia.pos.data.local.readClients
import com.amaxonia.pos.data.local.readProducts
import com.amaxonia.pos.data.local.saveClients
import com.amaxonia.pos.data.local.saveProducts
import com.amaxonia.pos.data.local.setInitialSyncCompleted
import com.amaxonia.pos.data.remote.dto.ClientDto
import com.amaxonia.pos.data.remote.dto.ProductDto
import com.amaxonia.pos.data.sync.OfflineSyncScope
import com.amaxonia.pos.data.sync.OfflineSyncSettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PurgeOfflineCatalogDataUseCaseTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var localStore: LocalStore
    private lateinit var offlineSyncSettingsStore: OfflineSyncSettingsStore
    private lateinit var useCase: PurgeOfflineCatalogDataUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        localStore = LocalStore(context)
        offlineSyncSettingsStore = OfflineSyncSettingsStore(context)
        useCase =
            PurgeOfflineCatalogDataUseCase(
                database = database,
                localStore = localStore,
                offlineSyncSettingsStore = offlineSyncSettingsStore,
                appContext = context,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `purge elimina todos los catalogos descargados pero preserva ventas pendientes y transaction log`() =
        runTest {
            // 1. Poblar catálogos en Room
            database.productDao().insertAll(
                listOf(
                    ProductEntity(
                        id = "prod-1",
                        code = "P01",
                        description = "Coca Cola",
                        reference = "REF-1",
                        barcode1 = "123",
                        barcode2 = "",
                        barcode3 = "",
                        department = 1,
                        isExempt = false,
                        taxRate = 7.0,
                        costActual = 1.0,
                        prices = emptyList(),
                    ),
                ),
            )
            database.clientDao().insertAll(
                listOf(
                    ClientEntity(
                        id = "cli-1",
                        code = "CLI01",
                        identification = "123-456",
                        dv = "0",
                        name = "Empresa X",
                        lastName = "SA",
                        address = "Calle 50",
                        phone = "123456",
                        email = "info@x.com",
                        status = true,
                        clientTypeId = 1,
                        taxpayerTypeId = 1,
                        countryId = 1,
                        addressLevel1 = "08",
                        addressLevel2 = "",
                        addressLevel3 = "",
                    ),
                ),
            )
            database.clientSucursalDao().insertAll(
                listOf(
                    ClientSucursalEntity(
                        sucursalId = 10,
                        clienteCodigo = "123-456",
                        nombreSucursal = "Sucursal Principal",
                    ),
                ),
            )
            database.promocionDao().upsertPromociones(
                listOf(
                    PromocionEntity(
                        id = "promo-1",
                        codigo = "PR01",
                        nombre = "2x1",
                        inicio = "2026-01-01",
                        fin = "2026-12-31",
                        imagen = "",
                        descuentoGlobal = 0.0,
                        idItem = "prod-1",
                        activo = true,
                    ),
                ),
            )
            database.paymentMethodDao().upsertAll(
                listOf(
                    PaymentMethodEntity(
                        idFormaPago = 1,
                        descripcion = "Efectivo",
                    ),
                ),
            )
            database.countryDao().insertAll(
                listOf(
                    CountryEntity(id = 1, iso = "PA", name = "Panamá"),
                ),
            )
            database.addressLevel1Dao().insertAll(
                listOf(
                    AddressLevel1Entity(countryCode = "PA", code = "08", name = "Panamá"),
                ),
            )
            database.syncStateDao().upsert(
                SyncStateEntity(tenantId = "T1", scope = "GLOBAL", cursor = 100L),
            )
            database.cajaSesionDao().upsert(
                CajaSesionEntity(
                    localId = "sesion-local-1",
                    tenantId = "T1",
                    cajaId = "caja-1",
                    openedAt = 1000L,
                    estado = "ABIERTA",
                ),
            )
            database.draftInvoiceDao().insert(
                DraftInvoiceEntity(
                    id = "draft-1",
                    itemsJson = "[]",
                    totalMinor = 1000L,
                    itemCount = 1,
                ),
            )

            // 2. Poblar DataStore
            localStore.saveProducts(listOf(ProductDto(id = "prod-1", code = "P01", description = "Coca Cola")))
            localStore.saveClients(listOf(ClientDto(id = "cli-1", name = "Empresa X", lastName = "SA", identification = "123-456")))
            localStore.setInitialSyncCompleted(companyId = 1, completed = true)
            localStore.setInitialSyncCompleted(companyId = 2, completed = true)
            offlineSyncSettingsStore.save(OfflineSyncScope(enabled = true, departmentIds = setOf(1), branchIds = setOf(10)))

            // 3. Poblar venta offline pendiente y transaction log
            database.pendingInvoiceDao().insert(
                PendingInvoiceEntity(
                    id = "sale-pending-1",
                    countryCode = "PA",
                    payloadJson = """{"idFactura":"sale-pending-1","total":25.0}""",
                    localInvoiceNumber = "001-001",
                    clientName = "Cliente Offline",
                    tenantId = "T1",
                    totalMinor = 2500L,
                ),
            )
            database.transactionLogDao().upsert(
                TransactionLogEntity(
                    clientCorrelationId = "sale-pending-1",
                    idCaja = "caja-1",
                    idCajaSecuencia = "sec-1",
                    totalAmount = 25.0,
                    currency = "USD",
                    clientName = "Cliente Offline",
                    tenantId = "T1",
                    remoteInvoiceId = null,
                    remoteInvoiceNumber = null,
                    status = "PENDING",
                    totalAmountMinor = 2500L,
                    currencyCode = "USD",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )

            // Verificar que antes de la purga hay datos cargados
            assertEquals(1, database.productDao().count())
            assertEquals(1, database.clientDao().count())
            assertTrue(localStore.readProducts().isNotEmpty())
            assertTrue(localStore.isInitialSyncCompleted(1))
            assertTrue(offlineSyncSettingsStore.load().enabled)

            // 4. EJECUTAR PURGA
            useCase()

            // 5. VERIFICAR QUE SE BORRARON LOS CATÁLOGOS DESCARGADOS
            assertEquals(0, database.productDao().count())
            assertEquals(0, database.clientDao().count())
            assertTrue(database.clientSucursalDao().getByClientCode("123-456").isEmpty())
            assertTrue(database.promocionDao().getAllActive().isEmpty())
            assertTrue(database.paymentMethodDao().getAll().isEmpty())
            assertTrue(database.countryDao().getAll().isEmpty())
            assertTrue(database.addressLevel1Dao().getByCountry("PA").isEmpty())
            assertTrue(database.syncStateDao().get("T1", "GLOBAL") == null)
            assertTrue(database.cajaSesionDao().getAbierta("T1") == null)
            assertEquals(0, database.draftInvoiceDao().count())

            // DataStore limpio
            assertTrue(localStore.readProducts().isEmpty())
            assertTrue(localStore.readClients().isEmpty())
            assertFalse(localStore.isInitialSyncCompleted(1))
            assertFalse(localStore.isInitialSyncCompleted(2))
            assertFalse(offlineSyncSettingsStore.load().enabled)

            // 6. VERIFICAR QUE LAS VENTAS PENDIENTES Y TRANSACTION LOG SE PRESERVAN
            val pendingSales = database.pendingInvoiceDao().getPending()
            assertEquals(1, pendingSales.size)
            assertEquals("sale-pending-1", pendingSales[0].id)
            assertEquals("Cliente Offline", pendingSales[0].clientName)
            assertEquals(2500L, pendingSales[0].totalMinor)

            val log = database.transactionLogDao().findById("sale-pending-1")
            assertEquals("sale-pending-1", log?.clientCorrelationId)
            assertEquals("PENDING", log?.status)
        }
}
