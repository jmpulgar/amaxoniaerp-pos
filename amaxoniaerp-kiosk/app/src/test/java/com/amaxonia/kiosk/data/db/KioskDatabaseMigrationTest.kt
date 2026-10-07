package com.amaxonia.kiosk.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KioskDatabaseMigrationTest {
    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Creates the exact v1 schema Room generated before the `method` column existed. */
    private fun createVersion1Database() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS `pending_payments` (`orderId` TEXT NOT NULL, " +
                                    "`transactionId` TEXT NOT NULL, `authCode` TEXT NOT NULL, `reference` TEXT NOT NULL, " +
                                    "`last4` TEXT NOT NULL, `brand` TEXT NOT NULL, `amount` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                                    "`createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`orderId`))",
                            )
                            db.execSQL(
                                "INSERT INTO pending_payments VALUES " +
                                    "('ord-v1', 'TXN', 'AUT', 'REF', '4242', 'VISA', '9.10', 'PENDING', 1, 0, NULL)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply {
            writableDatabase.close()
            close()
        }
    }

    @Test
    fun `migration 1 to 2 keeps unsynced payments and marks them as CARD`() =
        runBlocking {
            createVersion1Database()

            val db =
                KioskDatabase
                    .withMigrations(Room.databaseBuilder(context, KioskDatabase::class.java, dbName))
                    .allowMainThreadQueries()
                    .build()
            val migrated = db.pendingPaymentDao().getByOrderId("ord-v1")
            db.pendingPaymentDao().insert(migrated!!.copy(orderId = "ord-v2", method = "YAPPY"))

            assertEquals("CARD", migrated.method)
            assertEquals(PendingPayment.STATUS_PENDING, migrated.status)
            assertEquals("YAPPY", db.pendingPaymentDao().getByOrderId("ord-v2")?.method)
            db.close()
        }

    @Test
    fun `migration up to 3 adds an empty paymentMethodId and stores the chosen card method`() =
        runBlocking {
            createVersion1Database()

            val db =
                KioskDatabase
                    .withMigrations(Room.databaseBuilder(context, KioskDatabase::class.java, dbName))
                    .allowMainThreadQueries()
                    .build()
            val migrated = db.pendingPaymentDao().getByOrderId("ord-v1")
            db.pendingPaymentDao().insert(migrated!!.copy(orderId = "ord-v3", paymentMethodId = 49))

            assertNull(migrated.paymentMethodId)
            assertEquals(49, db.pendingPaymentDao().getByOrderId("ord-v3")?.paymentMethodId)
            db.close()
        }
}
