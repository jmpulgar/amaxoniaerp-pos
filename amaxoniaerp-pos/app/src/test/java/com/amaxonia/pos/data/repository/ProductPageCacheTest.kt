package com.amaxonia.pos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.remote.dto.ProductDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Semántica del fallback offline de páginas de productos (TASK-082): búsqueda
 * LIKE normalizada sobre la caché Room, filtro por departamento y tope del
 * catálogo completo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductPageCacheTest {
    private lateinit var db: AppDatabase
    private lateinit var cache: ProductPageCache

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        cache = ProductPageCache(db.productDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sin query pagina el catalogo ordenado por descripcion`() =
        runTest {
            seed(product("3", code = "C003", description = "Tostadora"))
            seed(product("1", code = "C001", description = "Cafetera"))
            seed(product("2", code = "C002", description = "Batidora"))

            val page = cache.page(departmentId = null, query = null, limit = 2, offset = 0)

            // Orden alfabetico por descripcion: Batidora, Cafetera, Tostadora.
            assertEquals(listOf("2", "1"), page.map { it.id })
        }

    @Test
    fun `la busqueda LIKE es insensible a mayusculas y cubre codigo descripcion y barcode`() =
        runTest {
            seed(product("1", code = "cafe-01", description = "Cafetera"))
            seed(product("2", code = "lic-02", description = "Licuadora", barcode1 = "cafe-02"))
            seed(product("3", code = "tost-03", description = "Tostadora"))

            val page = cache.page(departmentId = null, query = "cafe", limit = 10, offset = 0)

            assertEquals(setOf("1", "2"), page.map { it.id }.toSet())
        }

    @Test
    fun `una query en blanco actua como comodin`() =
        runTest {
            seed(product("1", code = "a", description = "Uno"))
            seed(product("2", code = "b", description = "Dos"))

            val blank = cache.page(departmentId = null, query = "   ", limit = 10, offset = 0)

            assertEquals(2, blank.size)
        }

    @Test
    fun `query con departamento filtra por ambos`() =
        runTest {
            seed(product("1", code = "a", description = "Vino tinto", department = "3"))
            seed(product("2", code = "b", description = "Vino blanco", department = "3"))
            seed(product("3", code = "c", description = "Vino sin alcohol", department = "4"))

            val page = cache.page(departmentId = 3, query = "vino", limit = 10, offset = 0)

            assertEquals(setOf("1", "2"), page.map { it.id }.toSet())
        }

    @Test
    fun `fullCatalog sirve hasta mil filas del catalogo cacheado`() =
        runTest {
            val bulk =
                (1..FULL_CATALOG_PLUS_ONE).map { index ->
                    product(id = index.toString(), code = "c$index", description = "p$index").toEntity()
                }
            db.productDao().insertAll(bulk)

            val catalog = cache.fullCatalog()

            assertEquals(FULL_CACHE_LIMIT, catalog.size)
        }

    private suspend fun seed(dto: ProductDto) {
        db.productDao().insertAll(listOf(dto.toEntity()))
    }

    private fun product(
        id: String,
        code: String,
        description: String,
        department: String = "0",
        barcode1: String = "",
    ): ProductDto =
        ProductDto(
            id = id,
            code = code,
            description = description,
            department = department,
            barcode1 = barcode1,
        )

    private companion object {
        const val FULL_CACHE_LIMIT = 1000
        const val FULL_CATALOG_PLUS_ONE = 1001
    }
}
