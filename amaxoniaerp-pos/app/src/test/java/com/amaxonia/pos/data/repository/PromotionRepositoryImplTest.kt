package com.amaxonia.pos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.PromocionDetalleEntity
import com.amaxonia.pos.data.local.db.PromocionEntity
import com.amaxonia.pos.data.local.security.SecureKeyValueStore
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiService
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromotionRepositoryImplTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PromotionRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val fakeSecure =
            object : SecureKeyValueStore {
                val map = mutableMapOf<String, String>()

                override fun readString(key: String): String? = map[key]

                override fun writeString(
                    key: String,
                    value: String,
                ) {
                    map[key] = value
                }

                override fun remove(key: String) {
                    map.remove(key)
                }
            }
        val store = LocalStore(context, fakeSecure)
        val apiService = ApiService(ApiClient(ApiConfigManager()))
        repository =
            PromotionRepositoryImpl(
                apiService = apiService,
                localStore = store,
                promocionDao = db.promocionDao(),
                productDao = db.productDao(),
                networkMonitor = SettableNetworkMonitor(context, false),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun toDomainPromotionUsesItemDescripcionWhenProductNotInCache() =
        runTest {
            val promoEntity =
                PromocionEntity(
                    id = "promo-1",
                    codigo = "P01",
                    inicio = null,
                    fin = null,
                    nombre = "Promo Combo",
                    imagen = "",
                    descuentoGlobal = 0.0,
                    idItem = "16",
                    activo = true,
                )
            val detalleEntity =
                PromocionDetalleEntity(
                    id = "det-1",
                    promocionId = "promo-1",
                    idItem = "16",
                    idTipoPrecio = "1",
                    cantidad = 1.0,
                    cantidadTotal = 1.0,
                    unidadEmpaque = "UND",
                    descuento = 0.0,
                    descuentoMonto = 0.0,
                    precio = 5.0,
                    impuesto = 0.0,
                    impuestoPorcentaje = 0.0,
                    importe = 5.0,
                    grupo = "1",
                    itemDescripcion = "Coca Cola 350ml",
                    itemCodigo = "CC350",
                )
            db.promocionDao().insertPromociones(listOf(promoEntity))
            db.promocionDao().insertDetalles(listOf(detalleEntity))

            val result = repository.getPromotionById("promo-1")
            assertTrue(result.isSuccess)
            val domainPromo = result.getOrThrow()
            val detalle = domainPromo.detalles.first()
            assertEquals("Coca Cola 350ml", detalle.productName)
            assertEquals("CC350", detalle.productCode)
            assertEquals("Coca Cola 350ml", detalle.product.description)
            assertEquals("CC350", detalle.product.code)
        }
}
