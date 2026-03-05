package com.amaxonia.pos.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ClientEntity::class,
        ProductEntity::class,
        CountryEntity::class,
        AddressLevel1Entity::class,
        AddressLevel2Entity::class,
        AddressLevel3Entity::class,
        ClientTypeEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun productDao(): ProductDao
    abstract fun countryDao(): CountryDao
    abstract fun addressLevel1Dao(): AddressLevel1Dao
    abstract fun addressLevel2Dao(): AddressLevel2Dao
    abstract fun addressLevel3Dao(): AddressLevel3Dao
    abstract fun clientTypeDao(): ClientTypeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clients ADD COLUMN addressLevel1 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clients ADD COLUMN addressLevel2 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clients ADD COLUMN addressLevel3 TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS countries (" +
                        "id INTEGER NOT NULL, " +
                        "iso TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "PRIMARY KEY(id)" +
                        ")"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS address_level1 (" +
                        "countryCode TEXT NOT NULL, " +
                        "code TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "PRIMARY KEY(countryCode, code)" +
                        ")"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS address_level2 (" +
                        "countryCode TEXT NOT NULL, " +
                        "code TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "PRIMARY KEY(countryCode, code)" +
                        ")"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS address_level3 (" +
                        "countryCode TEXT NOT NULL, " +
                        "code TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "PRIMARY KEY(countryCode, code)" +
                        ")"
                )
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clients ADD COLUMN clientTypeId INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS client_types (" +
                        "id INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "PRIMARY KEY(id)" +
                        ")"
                )
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN barcode2 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN barcode3 TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN isExempt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE products SET isExempt = CASE WHEN IFNULL(taxRate, 0) <= 0 THEN 1 ELSE 0 END")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "amaxonia_pos.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
